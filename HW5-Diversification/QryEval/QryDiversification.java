/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import javafx.beans.binding.DoubleExpression;

import java.io.*;
import java.lang.reflect.Array;
import java.util.*;

/**
 *  The query diversification re-ranking for Indri and BM25 retrieval model.
 */
public class QryDiversification {

    private double lambda;
    private String algorithm, intentsFile = "", initRankingFile = "";
    private int inputRankingLen, resultRankingLen;
    private HashMap<String, ScoreList> initQryRanking;
    private HashMap<String, HashMap<String, ScoreList>> initIntentRanking;
    private HashMap<String, ArrayList<String>> QryIntents;
    private HashMap<String, ArrayList<String>> intentBody;

    public void initialize(Map<String, String> param, RetrievalModel model) throws IOException{
        // check the occurrence of required parameters
        if(!(param.containsKey("diversity:lambda") &&
                param.containsKey("diversity:algorithm") &&
                param.containsKey("diversity:maxInputRankingsLength") &&
                param.containsKey("diversity:maxResultRankingLength"))) {
            throw new IllegalArgumentException
                    ("Required parameters for Diversification were missing from the parameter file.");
        }

        this.lambda = Double.parseDouble(param.get("diversity:lambda"));
        if(this.lambda < 0 || this.lambda > 1) throw new IllegalArgumentException
                (String.format("Illegal argument: %f, lambda is a real number between 0.0 and 1.0", this.lambda));

        this.algorithm = param.get("diversity:algorithm").toLowerCase();
        if(param.containsKey("diversity:intentsFile"))
            this.intentsFile = param.get("diversity:intentsFile");
        if(param.containsKey("diversity:initialRankingFile"))
            this.initRankingFile = param.get("diversity:initialRankingFile");

        this.inputRankingLen = Integer.parseInt(param.get("diversity:maxInputRankingsLength"));
        if(this.inputRankingLen < 0) throw new IllegalArgumentException
                (String.format("Illegal argument: %d, maxInputRankingsLength is an integer > 0", this.inputRankingLen));

        this.resultRankingLen = Integer.parseInt(param.get("diversity:maxResultRankingLength"));
        if(this.resultRankingLen < 0) throw new IllegalArgumentException
                (String.format("Illegal argument: %d, maxResultRankingLength is an integer > 0", this.resultRankingLen));
    }


    public void run(Map<String, String> parameters, RetrievalModel model) throws Exception {
        initialize(parameters, model);

        // check whether the initial ranking files and intents have been provided
        if(!this.initRankingFile.equals(""))
            cacheInitialRankings();
        else
            cacheIntents();   // fetch intents from file

        processQueryFile(parameters.get("queryFilePath"), model);

//         unitTest_PM2();
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
        do {
            line = scan.nextLine();
            String[] tuple = line.split(" ");
            String id = tuple[0].trim();
            int docid = Idx.getInternalDocid(tuple[2].trim());

            if(!id.equals(currId)) {  // finish caching for a query/intent ranking
                if(currId != "-1") {
                    if(currId.contains(".")) {     // this is a query intent ranking
                        int d = currId.indexOf(".");
                        String qid = currId.substring(0, d);
                        String intentId = currId.substring(d + 1);

                        if(!this.QryIntents.containsKey(qid)) {
                            this.QryIntents.put(qid, new ArrayList<>());
                            this.initIntentRanking.put(qid, new HashMap<>());
                        }

                        this.QryIntents.get(qid).add(intentId);
                        this.initIntentRanking.get(qid).put(intentId, r);
                    } else              // this is an query ranking
                        this.initQryRanking.put(currId, r);
                }
                currId = id;
                r = new ScoreList();
            }
            r.add(docid, Double.parseDouble(tuple[4].trim()));

        } while(scan.hasNext());

        if(currId != "-1"){
            if(currId.contains(".")) {     // this is a query intent ranking
                int d = currId.indexOf(".");
                String qid = currId.substring(0, d);
                String intentId = currId.substring(d + 1);

                if(!this.QryIntents.containsKey(qid)) {
                    this.QryIntents.put(qid, new ArrayList<>());
                    this.initIntentRanking.put(qid, new HashMap<>());
                }

                this.QryIntents.get(qid).add(intentId);
                this.initIntentRanking.get(qid).put(intentId, r);
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
            if(!this.intentBody.containsKey(qid))
                this.intentBody.put(qid, new ArrayList<>());
            this.intentBody.get(qid).add(tuple[1].trim());
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
//                System.out.println(qid + " " + query);

                // fetch the rankings for query and its intents
                ScoreList qryScore;   // initial ranking for a query
                HashMap<String, ScoreList> intentScores = new HashMap<>(); // initial rankings for intents
                ArrayList<String> intents = this.QryIntents.get(qid);

                // check whether the initial ranking files and intents have been provided
                if(!this.initRankingFile.equals("")){
                    qryScore = this.initQryRanking.get(qid);
                    intentScores = this.initIntentRanking.get(qid);
                }
                else{
                    qryScore = QryEval.processQuery(Integer.parseInt(qid), query, model);
                    qryScore.sort();

                    for(int i = 0; i < intents.size(); i++){
                        String intent = intents.get(i);
                        String body = this.intentBody.get(qid).get(i);
                        ScoreList s = QryEval.processQuery(0, body, model);
                        s.sort();
                        intentScores.put(intent, s);
//                        QryEval.printResults(intent, initial);
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
                                ("Unknown diversification algorithm " + this.algorithm);
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
     * Perform truncate and scaling on all rankings.
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
        boolean scale = false;    // flag to indicate whether scaling should perform

        // truncate the rankings
        int rankingLen = Math.min(inputRankingLen, qryScore.size());
        for(int i = 0; i < rankingLen; i++){
            rankOfDocid.put(qryScore.getDocid(i), i);
            ArrayList<Double> arr = new ArrayList<>(Collections.nCopies(numOfIntents + 1, 0.0));
            double s = qryScore.getDocidScore(i);
            if(s > 1.0) scale = true;
            arr.set(0, s);
            scores.add(arr);
        }

        for(String i: intentScores.keySet()){
            ScoreList r = intentScores.get(i);
            for(int j = 0; j < rankingLen; j++){
                int docid = r.getDocid(j);
                if(rankOfDocid.containsKey(docid)){
                    int index = rankOfDocid.get(docid);
                    double s = r.getDocidScore(j);
                    if(s > 1.0) scale = true;
                    scores.get(index).set(Integer.parseInt(i), s);
                }
            }
        }

        // there exists value > 1.0, perform scaling
        if(scale){
            // find maximal sum of scores across rankings
            ArrayList<Double> sumOfScores = new ArrayList<>(Collections.nCopies(numOfIntents + 1, 0.0));
            for(int i = 0; i < scores.size(); i++) {
                ArrayList<Double> qryEntry = scores.get(i);
//                System.out.print(i + " ");
                for(int j = 0; j < numOfIntents + 1; j++){
                    sumOfScores.set(j, sumOfScores.get(j) + qryEntry.get(j));
//                    System.out.print(scores.get(i).get(j) + " ");
                }
//                System.out.println();
            }

            // scalar to perform scaling [0.0 1.0] on all rankings
            double scalar = Collections.max(sumOfScores);

            for(int i = 0; i < sumOfScores.size(); i++)
                System.out.println(i + " " + sumOfScores.get(i));

            for(int i = 0; i < scores.size(); i++) {
//            System.out.print(i + " ");
                for(int j = 0; j < numOfIntents + 1; j++){
                    scores.get(i).set(j, scores.get(i).get(j) / scalar);
//                System.out.print(scores.get(i).get(j) + " ");
                }
//            System.out.println();
            }
        }
        return scores;
    }

    /**
     * Perform diversification and re-ranking using xQuAD.
     * @param scores scores of query and corresponding intents.
     * @return score list of the diversified ranking.
     * @throws IOException Error accessing the index
     */
    public ScoreList xQuAD(ArrayList<ArrayList<Double>> scores, HashMap<Integer, Integer> docidAtRank){
        ScoreList r = new ScoreList();
        int numOfIntents = scores.get(0).size() - 1;
        double w = 1.0 / numOfIntents;     // intent weight (assume uniform)

        HashSet<Integer> candidates = new HashSet<>();   // candidate documents
        for(int i = 0; i < scores.size(); i++)
            candidates.add(i);

        // score representing how well the selected set already covers each intent
        ArrayList<Double> coverageScore = new ArrayList<>(Collections.nCopies(numOfIntents + 1, 1.0));

        // selection progress
        while(r.size() < this.resultRankingLen){

            int winner = -1;     // selected document id for current rank
            double maxScore = -Double.MAX_VALUE;

            // compute score for each candidate
            for(int i: candidates){
                ArrayList<Double> qryScore = scores.get(i);
                double diversityScore = 0.0;
                for(int j = 1; j <= numOfIntents; j++)
                    diversityScore += qryScore.get(j) * coverageScore.get(j);
                diversityScore *= w;
                double s = (1 - this.lambda) * qryScore.get(0) + this.lambda * diversityScore;

                if(maxScore < s){   // update document with maximal score
                    maxScore = s;
                    winner = i;
                }
            }

            // finalize the winner for this round, remove from candidates set
            candidates.remove(winner);
            r.add(docidAtRank.get(winner), maxScore);
//            System.out.println(String.format("id: %d, score: %f", winner + 1, maxScore));
            for(int i = 1; i <= numOfIntents; i++)
                coverageScore.set(i, coverageScore.get(i) * (1.0 - scores.get(winner).get(i)));
        }
        return r;
    }

    /**
     * Perform diversification and re-ranking using PM2.
     * @param scores scores of query and corresponding intents.
     * @return score list of the diversified ranking.
     * @throws IOException Error accessing the index
     */
    public ScoreList PM2(ArrayList<ArrayList<Double>> scores, HashMap<Integer, Integer> docidAtRank){
        ScoreList r = new ScoreList();

        int numOfIntents = scores.get(0).size() - 1;
        // vote for each intent (assume uniform)
        double votes = this.resultRankingLen * 1.0 / numOfIntents;
        // slots already assigned for each intent
        ArrayList<Double> slots = new ArrayList<>(Collections.nCopies(numOfIntents + 1, 0.0));

        HashSet<Integer> candidates = new HashSet<>();   // candidate documents
        for(int i = 0; i < scores.size(); i++)
            candidates.add(i);

        // selection progress
        while(r.size() < this.resultRankingLen){

            // select intent that has the maximal quotient to fulfill
            int target = -1;
            double maxQuotient = -1.0;
            HashMap<Integer, Double> quotients = new HashMap<>();
            for(int i = 1; i <= numOfIntents; i++){
                double qt = votes / (2 * slots.get(i) + 1);
                quotients.put(i, qt);
                if(maxQuotient < qt){
                    maxQuotient = qt;
                    target = i;
                }
            }

            // select document that best fits the selected intent
            int winner = -1;     // selected document id for current rank
            double maxScore = -Double.MAX_VALUE;

            // compute score for each candidate
            for(int i: candidates){
                ArrayList<Double> qryScore = scores.get(i);

                // compute scores for covering the target intent
                double coverTargetIntent = maxQuotient * qryScore.get(target);

                // compute scores for covering other intents
                double coverOtherIntents = 0.0;    // the score of covering other intents
                for(int j = 1; j <= numOfIntents; j++) {
                    if (j == target) continue;  // skip the target intent of this pass
                    coverOtherIntents += qryScore.get(j) * quotients.get(j);
                }
                double s = this.lambda * coverTargetIntent + (1 - this.lambda) * coverOtherIntents;

                if(maxScore < s){   // update document with maximal score
                    maxScore = s;
                    winner = i;
                }
            }

            // finalize the winner for this round, remove from candidates set
            candidates.remove(winner);
            r.add(docidAtRank.get(winner), maxScore);
//            System.out.println(String.format("id: %d, score: %f", winner + 1, maxScore));

            // update occupied slots for each intent
            ArrayList<Double> qryScore = scores.get(winner);
            double sumOfScores = 0.0;
            for(int i = 1; i <= numOfIntents; i++)
                sumOfScores += qryScore.get(i);
            for(int i = 1; i <= numOfIntents; i++)
                slots.set(i, slots.get(i) + qryScore.get(i) / sumOfScores);
        }
        return r;
    }

//    public void unitTest_PM2(){
//        ArrayList<ArrayList<Double>> scores = new ArrayList<>();
//        ArrayList<Double> a = new ArrayList(Arrays.asList(0.7, 0.7, 0.2));
//        scores.add(a);
//        a = new ArrayList(Arrays.asList(0.69, 0.8, 0.1));
//        scores.add(a);
//        a = new ArrayList(Arrays.asList(0.68, 0.6, 0.3));
//        scores.add(a);
//        a = new ArrayList(Arrays.asList(0.67, 0.2, 0.7));
//        scores.add(a);
//        a = new ArrayList(Arrays.asList(0.66, 0.3, 0.8));
//        scores.add(a);
//        HashMap<Integer, Integer> docidAtRank = new HashMap<>();
//        this.resultRankingLen = 8;
//        for(int i = 0; i < 5; i++)
//            docidAtRank.put(i, i + 1);
//        ScoreList r = PM2(scores, docidAtRank);
//    }

}

