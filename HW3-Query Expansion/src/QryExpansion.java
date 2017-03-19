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

  public ScoreList getScoreList(int qid, Qry q, String originalQuery, RetrievalModelIndri model) throws IOException{

      ScoreList r = new ScoreList();

      if(!model.getFilePath("fbInitialRankingFile").equals(""))
          r = model.getInitialRanking(qid);
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
      double fbDocs = model.getParam("fbDocs");
      double fbTerms = model.getParam("fbTerms");
      Set<String> candidates = findCandidates(fbDocs, r);

      // Compute score for each candidate term
      PriorityQueue<Entry> pq = new PriorityQueue<>(new EntryComparator());
      int i = 0, size = candidates.size();
      for(String term: candidates) {
          double termScore = computeScore(term, fbDocs, r, model);
          pq.add(new Entry(term, termScore));
//          System.out.println(String.format("Term %d out of %d", i++, size));
          if(pq.size() > fbTerms) pq.poll();
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
          writeFile(qid, learnedQuery, outputPath);


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
     * @param qid Id of a query.
     * @param s A string that contains a query.
     * @param path The path of the file to write.
     * @throws IOException Error accessing the index
     */
    public void writeFile(int qid, String s, String path) throws IOException{
        PrintWriter writer = new PrintWriter(new FileWriter(path, true));
        writer.println(String.format("%d: %s", qid, s));
        writer.close();
    }

    /**
     * retrieve all the terms in the top K documents.
     * @param fbDocs number of top documents (K).
     * @param r ScoreList of the top 100 documents.
     * @return A hashset containing all the candidate terms.
     * @throws IOException Error accessing the index
     */
    public Set<String> findCandidates(double fbDocs, ScoreList r) throws IOException{
        Set<String> candidates = new HashSet<>();
        for (int i = 0; i < fbDocs; i++) {
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

  public double computeScore(String term, double fbDocs, ScoreList r, RetrievalModelIndri model) throws IOException{
      double score = 0.0;
      double mu = model.getParam("fbMu");
      String field = "body";
      for(int i = 0; i < fbDocs; i++){

          // Indri score for the original query for this document
          int docid = r.getDocid(i);
          double docScore = r.getDocid(i);

          // score for the candidate term conditioned on this document
          double lenCorpus = Idx.getSumOfFieldLengths(field);
          double ctf = Idx.getTotalTermFreq(field, term);
          double p = ctf / lenCorpus;      // MLE of Prob(term in the collection)

          double tf = 0.0;
          double docLen = Idx.getFieldLength(field, docid);
          QryIopTerm IopTerm = new QryIopTerm(term, field);
          IopTerm.initialize(model);
          IopTerm.docIteratorAdvanceTo(docid);
          if(IopTerm.docIteratorHasMatch(model) && IopTerm.docIteratorGetMatch() == docid)
              tf = IopTerm.docIteratorGetMatchPosting().tf;

          docScore *= (tf + mu * p) / (docLen + mu);

          // a form of idf to penalize frequent terms
          docScore *= Math.log(lenCorpus / ctf);
//          System.out.println(String.format("score for %s in doc %d: %f", term, i, docScore));

          score += docScore;
      }
      return score;
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

