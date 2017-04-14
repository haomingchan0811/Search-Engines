/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import javafx.beans.binding.DoubleExpression;

import java.io.*;
import java.util.*;

/**
 *  The query diversification re-ranking for Indri and BM25 retrieval model.
 */
public class QryDiversification {

    private double lambda;
    private String algorithm, intentsFile, initRankingFile = "";
    private int inputRankingLen, resultRankingLen;
    private RetrievalModel model;
    private HashMap<String, ScoreList> initQryRanking, initIntentRanking;
    private HashMap<String, ArrayList<String>> QryIntents;
    private HashMap<String, String> intentBody;

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
                param.containsKey("diversity:maxInputRankingsLength") &&
                param.containsKey("diversity:maxResultRankingsLength"))) {
            throw new IllegalArgumentException
                    ("Required parameters for Diversification were missing from the parameter file.");
        }

        this.lambda = Double.parseDouble(param.get("diversity:lambda"));
        if(this.lambda < 0 || this.lambda > 1) throw new IllegalArgumentException
                (String.format("Illegal argument: %f, lambda is a real number between 0.0 and 1.0", this.lambda));

        this.algorithm = param.get("diversity:algorithm").toLowerCase();
        this.intentsFile = param.get("diversity:intentsFile");
        if(param.containsKey("diversity:initialRankingFile"))
            this.initRankingFile = param.get("diversity:initialRankingFile");

        this.inputRankingLen = Integer.parseInt(param.get("diversity:maxInputRankingsLength"));
        if(this.inputRankingLen < 0) throw new IllegalArgumentException
                (String.format("Illegal argument: %d, maxInputRankingsLength is an integer > 0", this.inputRankingLen));

        this.resultRankingLen = Integer.parseInt(param.get("diversity:maxResultRankingsLength"));
        if(this.resultRankingLen < 0) throw new IllegalArgumentException
                (String.format("Illegal argument: %d, maxResultRankingsLength is an integer > 0", this.resultRankingLen));
    }


    public void run(Map<String, String> parameters, RetrievalModel model) throws Exception {
        initialize(parameters, model);

        // check whether the initial ranking files and intents have been provided
        if(!this.initRankingFile.equals(""))
            cacheInitialRankings();
        else
            cacheIntents();   // fetch intents from file

        processQueryFile(parameters.get("queryFilePath"), model);
    }

    /**
     *  read the relevance docs from file and cache in memory.
     *  @throws Exception Error accessing the Lucene index.
     */
    public void cacheInitialRankings() throws Exception {
        this.initQryRanking = new HashMap<>();
        this.initIntentRanking = new HashMap<>();
        this.QryIntents = new HashMap<>();

        String file = this.initRankingFile;
        File initRanking = new File(file);
        if(!initRanking.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + file);
        }

        Scanner scan = new Scanner(initRanking);
        String line = null;

        String currId = "-1";   // initialize query/intent id
        ScoreList r = new ScoreList();
        HashSet<Integer> relDocs = new HashSet<>();  // relevant documents of a query
        do {
            line = scan.nextLine();
            String[] tuple = line.split(" ");
            String id = tuple[0].trim();
            boolean isIntent = id.contains(".");
            int docid = Idx.getInternalDocid(tuple[2].trim());

            if(!id.equals(currId)) {  // finish caching for a query/intent ranking
                if(currId != "-1") {
                    if(currId.contains(".")) {     // this is a query intent ranking
                        int d = currId.indexOf(".");
                        String qid = currId.substring(0, d);
                        String intentId = currId.substring(d + 1);
                        if(!this.QryIntents.containsKey(qid))
                            this.QryIntents.put(qid, new ArrayList<>());
                        this.QryIntents.get(qid).add(intentId);
                        this.initIntentRanking.put(intentId, r);
                    } else              // this is an query ranking
                        this.initQryRanking.put(currId, r);
                }
                currId = id;
                r = new ScoreList();
            }
            if(isIntent){
                // check whether the document shows up in the initial ranking of the query
                if(relDocs.contains(docid))
                    r.add(docid, Double.parseDouble(tuple[4].trim()));
            }
            else{
                if(r.size() == 0)     // a new query
                    relDocs = new HashSet<>();    // reinitialize the set
                r.add(docid, Double.parseDouble(tuple[4].trim()));
                relDocs.add(docid);   // record the relevant documents
            }
        } while(scan.hasNext());

        if(currId != "-1"){
            if(currId.contains(".")) {     // this is a query intent ranking
                int d = currId.indexOf(".");
                String qid = currId.substring(0, d);
                String intentId = currId.substring(d + 1);
                if(!this.QryIntents.containsKey(qid))
                    this.QryIntents.put(qid, new ArrayList<>());
                this.QryIntents.get(qid).add(intentId);
                this.initIntentRanking.put(intentId, r);
            } else              // this is an query ranking
                this.initQryRanking.put(currId, r);
        }
        scan.close();
    }

    /**
     *  read the intents from file and cache in memory.
     *  @throws Exception Error accessing the Lucene index.
     */
    public void cacheIntents() throws Exception {
        this.QryIntents = new HashMap<>();
        this.intentBody = new HashMap<>();

        String file = this.intentsFile;
        File intents = new File(file);
        if(!intents.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + file);
        }

        Scanner scan = new Scanner(intents);
        String line = null;

        do {
            line = scan.nextLine();
            String[] tuple = line.split(":");
            String id = tuple[0].trim();
            int d = id.indexOf(".");
            String qid = id.substring(0, d);
            String intentId = id.substring(d + 1);
            if(!this.QryIntents.containsKey(qid))
                this.QryIntents.put(qid, new ArrayList<>());
            this.QryIntents.get(qid).add(intentId);
            this.intentBody.put(intentId, tuple[1].trim());
        } while(scan.hasNext());

        scan.close();
    }

    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @param model
     *  @throws IOException Error accessing the Lucene index.
     */
    public void processQueryFile(String queryFilePath, RetrievalModel model)
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

                String qid = qLine.substring(0, d);
                String query = qLine.substring(d + 1);

                // fetch the rankings for query and its intents
                ScoreList qryScore;   // initial ranking for a query
                HashMap<String, ScoreList> intentScores = new HashMap<>(); // initial rankings for intents
                ArrayList<String> intents = this.QryIntents.get(qid);

                // check whether the initial ranking files and intents have been provided
                if(!this.initRankingFile.equals("")){
                    qryScore = this.initQryRanking.get(qid);
                    for(int i = 0; i < intents.size(); i++){
                        String intent = intents.get(i);
                        intentScores.put(intent, this.initIntentRanking.get(intent));
                    }
                }
                else{
                    qryScore = QryEval.processQuery(Integer.parseInt(qid), query, model);
                    HashSet<Integer> relDocs = new HashSet<>();  // relevant documents of a query
                    for(int i = 0; i < qryScore.size(); i++)
                        relDocs.add(qryScore.getDocid(i));

                    for(int i = 0; i < intents.size(); i++){
                        String intent = intents.get(i);
                        String body = this.intentBody.get(intent);
                        ScoreList initial = QryEval.processQuery(0, body, model);

                        // eliminate those which doesn't show up in query ranking
                        ScoreList s = new ScoreList();
                        for(int j = 0; j < initial.size(); j++){
                            int docid = initial.getDocid(j);
                            if(relDocs.contains(docid))
                                s.add(docid, initial.getDocidScore(j));
                        }
                        intentScores.put(intent, s);
                    }
                }

                // perform scaling on document scores
                ArrayList<ArrayList<Double>> scores = scaling(qryScore, intentScores);

                // docid at rank i of this query
                HashMap<Integer, Integer> docidAtRank = new HashMap<>();
                for(int i = 0; i < qryScore.size(); i++)
                    docidAtRank.put(i, qryScore.getDocid(i));

                // perform diversified ranking
                ScoreList r;
                switch (this.algorithm){
                    case "pm2":
                        r = PM2(scores, docidAtRank);
                        break;
                    case "xquad":
                        r = xQuAD(scores, docidAtRank);
                        break;
                    default:
                        throw new IllegalArgumentException
                                ("Unknown diversification algirithm " + this.algorithm);
                }
                r.sort();
                if(r != null) {
                    QryEval.printResults(qid, r);
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
     * Perform scaling on all rankings.
     * @param qryScore initial ranking for the query.
     * @param intentScores initial rankings for the query intents.
     * @return arraylist of the scores of query and corresponding intents.
     * @throws IOException Error accessing the index
     */
    public ArrayList<ArrayList<Double>> scaling(ScoreList qryScore, HashMap<String, ScoreList> intentScores) {

        // A list to store query score and corresponding intents' scores
        ArrayList<ArrayList<Double>> scores = new ArrayList<>();
        int numOfIntents = intentScores.size();
        HashMap<Integer, Integer> rankOfDocid = new HashMap<>();

        for(int i = 0; i < qryScore.size() && i < this.inputRankingLen; i++){
            rankOfDocid.put(qryScore.getDocid(i), i);
            ArrayList<Double> arr = new ArrayList<>(Collections.nCopies(numOfIntents + 1, 0.0));
            arr.set(0, qryScore.getDocidScore(i));
            scores.add(arr);
        }

        for(String i: intentScores.keySet()){
            ScoreList s = intentScores.get(i);
            for(int j = 0; j < s.size(); j++){
                int docid = s.getDocid(j);
                if(rankOfDocid.containsKey(docid)){
                    int index = rankOfDocid.get(docid);
                    scores.get(index).set(Integer.parseInt(i), s.getDocidScore(j));
                }
            }
        }

        // find maximal sum of scores across rankings
        ArrayList<Double> sumOfScores = new ArrayList<>(Collections.nCopies(numOfIntents + 1, 0.0));
        for(int i = 0; i < scores.size(); i++) {
            ArrayList<Double> qry = scores.get(i);
            System.out.print(i + " ");
            for(int j = 0; j < qry.size(); j++){
                sumOfScores.set(j, sumOfScores.get(j) + qry.get(j));
                System.out.print(scores.get(i).get(j) + " ");
            }
            System.out.println();
        }

        // scalar to perform scaling [0.0 1.0] on all rankings
        double scalar = Collections.max(sumOfScores);
        for(int i = 0; i < scores.size(); i++) {
            System.out.print(i + " ");
            for(int j = 0; j < numOfIntents + 1; j++){
                scores.get(i).set(j, scores.get(i).get(j) / scalar);
                System.out.print(scores.get(i).get(j) + " ");
            }
            System.out.println();
        }
        return scores;
    }

    /**
     * Perform diversification and re-ranking using xQuAD.
     * @param scores scores of query and corresponding intents.
     * @param docidAtRank docid at rank i of this query.
     * @return score list of the diversified ranking.
     * @throws IOException Error accessing the index
     */
    public ScoreList xQuAD(ArrayList<ArrayList<Double>> scores, HashMap<Integer, Integer> docidAtRank){
        ScoreList r = new ScoreList();

        return r;
    }

    /**
     * Perform diversification and re-ranking using PM2.
     * @param scores scores of query and corresponding intents.
     * @param docidAtRank docid at rank i of this query.
     * @return score list of the diversified ranking.
     * @throws IOException Error accessing the index
     */
    public ScoreList PM2(ArrayList<ArrayList<Double>> scores, HashMap<Integer, Integer> docidAtRank){
        ScoreList r = new ScoreList();


        return r;
    }



}

