/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 *  The query expansion process for Indri retrieval model.
 */
public class QryExpansion {

    private double fbMu, lenCorpus, fbDocs, fbTerms;
    private String field;
    private int qid;
    private RetrievalModelIndri model;

    // A utility class to create a <term, score> object.
    private class Entry {
        private String key;
        private double value;

        public Entry(String key, double value) {
            this.key = key;
            this.value = value;
        }

        public String getKey(){
            return this.key;
        }

        public double getValue(){
            return this.value;
        }
    }

    // customized comparator for priority queue: sort pair with ascending value
    class EntryComparator implements Comparator<Entry> {

        @Override
        public int compare(Entry a, Entry b) {
            return Double.compare(a.getValue(), b.getValue());
        }
    }


    public void initialize(RetrievalModelIndri model, int qid) throws IOException{
        this.model = model;
        this.field = "body";
        this.qid = qid;
        this.fbMu = model.getParam("fbMu");
        this.lenCorpus = Idx.getSumOfFieldLengths(this.field);
        this.fbTerms = model.getParam("fbTerms");
        this.fbDocs= model.getParam("fbDocs");
    }

  public ScoreList getScoreList(int qid, Qry q, String originalQuery, RetrievalModelIndri model) throws IOException{

      // initialize parameter
      initialize(model, qid);

      ScoreList r = new ScoreList();

      if(!model.getFilePath("fbInitialRankingFile").equals(""))
          r = model.getInitialRanking(this.qid);
      else{
          q.initialize(model);

          while (q.docIteratorHasMatch(model)) {
              int docid = q.docIteratorGetMatch();
              double score = ((QrySop) q).getScore(model);
              r.add(docid, score);
              q.docIteratorAdvancePast(docid);
          }
      }
      r.sort();

      /* extract all candidate terms */
      Set<String> candidates = findCandidates(r);

      // Compute score for each candidate term
      PriorityQueue<Entry> pq = new PriorityQueue<>(new EntryComparator());

      int i = 0, size = candidates.size();
      for(String term: candidates) {
          double termScore = computeScore(term, r);
          pq.add(new Entry(term, termScore));
          System.out.println(String.format("Term %d out of %d", i++, size));
          if(pq.size() > this.fbTerms) pq.poll();
      }

      // expand the query
      String learnedQuery = "#wand(";
      while(pq.size() > 0) {
          Entry temp = pq.poll();
          learnedQuery += String.format(" %f %s", temp.getValue(), temp.getKey());
      }
      learnedQuery += ")";

      // write the expanded query to a file
      String outputPath = model.getFilePath("fbExpansionQueryFile");
      if(!outputPath.equals(""))
          writeFile(learnedQuery, outputPath);


      // rewrite the query by combining the expanded query with the original one
      double orginWeight = model.getParam("fbOrigWeight");
      String expandedQuery = String.format("#wand(%f %s %f %s)", orginWeight, originalQuery, 1 - orginWeight, learnedQuery);
      // System.out.println(expandedQuery);

      // run the expanded query to retrieve documents
      r = processQuery(expandedQuery, model);

      return r;
  }

    /**
     * write the query to file along with its id.
     * @param s A string that contains a query.
     * @param path The path of the file to write.
     * @throws IOException Error accessing the index
     */
    public void writeFile(String s, String path) throws IOException{
        PrintWriter writer = new PrintWriter(new FileWriter(path, true));
        writer.println(String.format("%d: %s", this.qid, s));
        writer.close();
    }

    /**
     * retrieve all the terms in the top K documents.
     * @param r ScoreList of the top 100 documents.
     * @return A hashset containing all the candidate terms.
     * @throws IOException Error accessing the index
     */
    public Set<String> findCandidates(ScoreList r) throws IOException{
        Set<String> candidates = new HashSet<>();
        for (int i = 0; i < this.fbDocs; i++) {
            int docid = r.getDocid(i);
            TermVector vec = new TermVector(docid, "body");
            int numTerms = vec.stemsLength();

            for (int k = 1; k < numTerms; k++) {
                String term = vec.stemString(k);
                if (!term.contains("."))   // terms having "." may confuse the parser
                    candidates.add(term);
            }
        }
        return candidates;
    }

    /**
     * Process one query.
     * @param qString A string that contains a query.
     * @param model The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model) throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        if (q != null) {
            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            return r;
        }
        else return null;
    }

  public double computeScore(String term, ScoreList r) throws IOException{
      double score = 0.0;

      // a form of idf to penalize frequent terms
      double ctf = Idx.getTotalTermFreq(this.field, term);
      double p = ctf / this.lenCorpus;      // MLE of Prob(term in the collection)
      double idf = Math.log(this.lenCorpus / ctf);


      for(int i = 0; i < this.fbDocs; i++){

          // Indri score for the original query for this document
          int docid = r.getDocid(i);
          double docScore = r.getDocid(i);
          TermVector vec = new TermVector(docid, this.field);

          // score for the candidate term conditioned on this document
          int idx = vec.indexOfStem(term);   // index of the term in this document
          double tf = (idx == -1? 0.0: vec.stemFreq(idx));
          double docLen = Idx.getFieldLength(this.field, docid);
          docScore *= (tf + this.fbMu * p) / (docLen + this.fbMu);

//          System.out.println(String.format("score for %s in doc %d: %f", term, i, docScore));

          score += docScore;
      }
      return score * idf;
  }

//    /**
//     *  Sort a HashMap by its value.
//     */
//  public List sortByValue(HashMap<String, Double> map){
//      List list = new ArrayList(map.entrySet());
//
//      Collections.sort(list, new Comparator<Map.Entry>(){
//          public int compare(Map.Entry a, Map.Entry b){
//              return ((Comparable)(b).getValue()).compareTo((a).getValue());
//          }
//      });
//
//      return list;
//  }
}

