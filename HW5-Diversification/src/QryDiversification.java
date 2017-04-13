/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

/**
 *  The query diversification re-ranking for Indri and BM25 retrieval model.
 */
public class QryDiversification {

    private double lambda;
    private String algorithm, intentsFile, initRankingFile;
    private int inputRankingLen, resultRankingLen;
    private RetrievalModel model;

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


    public void initialize(Map<String, String> param, RetrievalModel model) throws IOException{
        this.model = model;
        // check the occurrence of required parameters
        if(!(param.containsKey("diversity:lambda") &&
                param.containsKey("diversity:algorithm") &&
                param.containsKey("diversity:intentsFile") &&
                param.containsKey("diversity:initialRankingFile") &&
                param.containsKey("diversity:maxInputRankingsLength") &&
                param.containsKey("diversity:maxResultRankingsLength"))) {
            throw new IllegalArgumentException
                    ("Required parameters for Diversification were missing from the parameter file.");
        }

        this.lambda = Double.parseDouble(param.get("diversity:lambda"));
        if(this.lambda < 0 || this.lambda > 1) throw new IllegalArgumentException
                (String.format("Illegal argument: %f, lambda is a real number between 0.0 and 1.0", this.lambda));

        this.algorithm = param.get("diversity:algorithm");
        this.intentsFile = param.get("diversity:intentsFile");
        this.initRankingFile = param.get("diversity:initialRankingFile");

        this.inputRankingLen = Integer.parseInt(param.get("diversity:maxInputRankingsLength"));
        if(this.inputRankingLen < 0) throw new IllegalArgumentException
                (String.format("Illegal argument: %d, maxInputRankingsLength is an integer > 0", this.inputRankingLen));

        this.resultRankingLen = Integer.parseInt(param.get("diversity:maxResultRankingsLength"));
        if(this.resultRankingLen < 0) throw new IllegalArgumentException
                (String.format("Illegal argument: %d, maxResultRankingsLength is an integer > 0", this.resultRankingLen));
    }

    public void run(Map<String, String> parameters, RetrievalModel model) throws IOException {
        initialize(parameters, model);
        processQueryFile(parameters.get("queryFilePath"), model);
        return;
    }


    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @param model
     *  @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath, RetrievalModel model)
            throws IOException {

        BufferedReader input = null;

        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.
            while((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(':');

                if(d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }

                // printMemoryUsage(false);
                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                ScoreList r = null;

                r = processQuery(Integer.parseInt(qid), query, model);
                r.sort();

                if(r != null) {
                    printResults(qid, r);
                }
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        finally {
            input.close();
        }
    }

    /**
     * Process one query.
     * @param qString A string that contains a query.
     * @param model The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(int qid, String qString, RetrievalModel model) throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        if (q != null) {
            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                if (model instanceof RetrievalModelIndri) {
                    RetrievalModelIndri Indri = (RetrievalModelIndri) model;
                    if(Indri.getFilePath("fb").equals("true")) {
                        QryExpansion QryExp = new QryExpansion();
                        return QryExp.getScoreList(qid, q, qString, Indri);
                    }
                }

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
//                System.out.println(docid + ": " + score);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            return r;
        } else
            return null;
    }


  public ScoreList getScoreList(int qid, Qry q, String originalQuery, RetrievalModelIndri model) throws IOException {

      // initialize parameter
      initialize(model, qid);

      if (!model.getFilePath("fbInitialRankingFile").equals("")) {
          this.r = model.getInitialRanking(this.qid);
          //System.out.println(model.getFilePath("fbInitialRankingFile"));
      } else {
          this.r = new ScoreList();
          q.initialize(model);

          while (q.docIteratorHasMatch(model)) {
              int docid = q.docIteratorGetMatch();
              double score = ((QrySop) q).getScore(model);
              this.r.add(docid, score);
              q.docIteratorAdvancePast(docid);
          }
      }
      this.r.sort();

      /* extract all candidate terms */
      Set<String> candidates = findCandidates();

      // Compute score for each candidate term
      PriorityQueue<Entry> pq = new PriorityQueue<>(new EntryComparator());

//      int i = 0, size = candidates.size();
      for (String term : candidates) {
          double termScore = computeScore(term);
          pq.add(new Entry(term, termScore));
//          System.out.println(String.format("Term %d out of %d", i++, size));
          if (pq.size() > this.fbTerms) pq.poll();
      }

      // expand the query
      String learnedQuery = "#wand(";
      while (pq.size() > 0) {
          Entry temp = pq.poll();
          learnedQuery += String.format(" %f %s", temp.getValue(), temp.getKey());
      }
      learnedQuery += ")";

//      // fetch learned query from previous generated file to speed up for experiment 3/4/5
//      String learnedQuery = this.queries.get(qid);
//      return processQuery(learnedQuery);

      // write the expanded query to a file
      if(!this.outputPath.equals(""))
          writeFile(learnedQuery);

      // rewrite the query by combining the expanded query with the original one
      double originWeight = model.getParam("fbOrigWeight");
      String expandedQuery = String.format("#wand(%f %s %f %s)", originWeight, originalQuery, 1 - originWeight, learnedQuery);

      // run the expanded query to retrieve documents
      return processQuery(expandedQuery);
  }

    /**
     * write the query to file along with its id.
     * @param s A string that contains a query.
     * @throws IOException Error accessing the index
     */
    public void writeFile(String s) throws IOException{
        PrintWriter writer = new PrintWriter(new FileWriter(this.outputPath, true));
        writer.println(String.format("%d: %s", this.qid, s));
        writer.close();
    }

    /**
     * retrieve all the terms in the top K documents.
     * @return A set containing all the candidate terms.
     * @throws IOException Error accessing the index
     */
    public Set<String> findCandidates() throws IOException{
        Set<String> candidates = new HashSet<>();
        for (int i = 0; i < this.fbDocs; i++) {
            TermVector vec = new TermVector(this.r.getDocid(i), "body");
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
     * @return Search results
     * @throws IOException Error accessing the index
     */
    public ScoreList processQuery(String qString) throws IOException {

        String defaultOp = this.model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        if (q != null) {
            ScoreList s = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(this.model);

                while (q.docIteratorHasMatch(this.model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(this.model);
                    s.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            return s;
        }
        else return null;
    }

    /**
     * compute the term score (with idf effect) of all top K documents.
     * @param term target candidate term.
     * @return the term score (with idf effect) of all top K documents.
     * @throws IOException Error accessing the index
     */
    public double computeScore(String term) throws IOException{
        double score = 0.0;

        // a form of idf to penalize frequent terms
        double ctf = Idx.getTotalTermFreq(this.field, term);
        double p = ctf / this.lenCorpus;      // MLE of Prob(term in the collection)
        double idf = Math.log(this.lenCorpus / ctf);

        for(int i = 0; i < this.fbDocs; i++){

            // Indri score for the original query for this document
            int docid = this.r.getDocid(i);
            double docScore = this.r.getDocidScore(i);
            TermVector vec = new TermVector(docid, this.field);

            // score for the candidate term conditioned on this document
            int idx = vec.indexOfStem(term);   // index of the term in this document
            double tf = (idx == -1? 0.0: vec.stemFreq(idx));
            double docLen = Idx.getFieldLength(this.field, docid);
            docScore *= (tf + this.fbMu * p) / (docLen + this.fbMu);
            //System.out.println(String.format("score for %s in doc %d: %f", term, i, docScore));

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

