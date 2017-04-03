/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

/**
 *  An object that stores parameters for the LearningToRanks
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelLeToR extends RetrievalModel {

    private Map<String, String> param;
    private HashMap<String, Double> pagerank = new HashMap<>(), avgDocLen = new HashMap<>();
    private HashMap<String, HashMap<String, String>> rel = new HashMap<>();
    private long numOfDocs;     // global stats of corpora
    private double k1, b, k3;   // parameters for BM25 model
    private double mu, lambda;  // parameters for Indri model
    private int numOfFeatures = 18;
    private HashSet<Integer> featureIdx = new HashSet<>(); // the index of selected features

    public String defaultQrySopName() {
		return null;
	}

	// set parameters for retrieval model
	public void setParameters(Map<String, String> parameters) {
	    this.param = parameters;
        for(int i = 1; i <= this.numOfFeatures; i++)   // initialize feature indices
            this.featureIdx.add(i);

	    // set desired features
        if(this.param.containsKey("letor:featureDisable")){
            String[] disabledIdx = this.param.get("letor:featureDisable").split(",");
            this.numOfFeatures -= disabledIdx.length;
            for(int i = 0; i < disabledIdx.length; i++)
                this.featureIdx.remove(Integer.parseInt(disabledIdx[i]));
        }

        // set parameters for BM25 model
        if(param.containsKey("BM25:k_1") && param.containsKey("BM25:k_3") && param.containsKey("BM25:b")) {
            this.k1 = Double.parseDouble(param.get("BM25:k_1"));
            if(this.k1 < 0) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("BM25:k_1") + ", k1 is a real number >= 0.0");

            this.b = Double.parseDouble(param.get("BM25:b"));
            if(this.b < 0 || this.b > 1) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("BM25:b") + ", b is a real number between 0.0 and 1.0");

            this.k3 = Double.parseDouble(param.get("BM25:k_3"));
            if(this.k3 < 0) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("BM25:k_3") + ", k3 is a real number >= 0.0");
        }

        // set parameters for Indri model
        if(param.containsKey("Indri:mu") && param.containsKey("Indri:lambda")) {
            String mu = param.get("Indri:mu");
            this.mu = Double.parseDouble(mu);
            if(this.mu < 0) throw new IllegalArgumentException
                    ("Illegal argument: " + mu + ", mu is a real number >= 0");

            String lambda = param.get("Indri:lambda");
            this.lambda = Double.parseDouble(lambda);
            if(this.lambda < 0) throw new IllegalArgumentException
                    ("Illegal argument: " + lambda + ", lambda is a real number between 0.0 and 1.0");
        }

    }

    // Fetch value of the parameter
    public double getParam(String s) { return 0; }

    public void learn(Map<String, String> parameters) throws Exception, IOException {
	    setParameters(parameters);
        this.numOfDocs = Idx.getNumDocs();
        cacheAvgDocLen();
        cachePageRank(this.param.get("letor:pageRankFile"));          // cache (externalId, pageRank)
        cacheRelevance(this.param.get("letor:trainingQrelsFile"));    // cache [qid, (externalId, relScore)]

        // training phase using RankSVM
        String trainFile = this.param.get("letor:trainingQueryFile");
        processQueryFile(trainFile, false);

        // runs svm_rank_learn from within Java to train the model
        TrainRankSVM();

        // testing phase using RankSVM
        String testFile = param.get("queryFilePath");
        processQueryFile(testFile, true);

        // runs svm_rank_classify from within Java to fetch scores for documents.
        TestRankSVM();

        // re-rank the documents according to the SVM output
//        reRank();
    }

    /**
     *  read the pageRanks from file and cache in memory.
     *  @param file
     *  @throws IOException Error accessing the Lucene index.
     */
    public void cachePageRank(String file) throws IOException{

        File pageRankFile = new File(file);
        if(!pageRankFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + file);
        }

        Scanner scan = new Scanner(pageRankFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("\t");
            this.pagerank.put(pair[0].trim(), Double.parseDouble(pair[1].trim()));
        } while(scan.hasNext());

        scan.close();
    }

    /**
     *  read the relevance docs and assessment from file and cache in memory.
     *  @param file
     *  @throws IOException Error accessing the Lucene index.
     */
    public void cacheRelevance(String file) throws IOException{
        this.rel = new HashMap<>();

        File pageRankFile = new File(file);
        if(!pageRankFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + file);
        }

        Scanner scan = new Scanner(pageRankFile);
        String line = null;

        String currId = "-1";   // initialize query id
        HashMap<String, String> relScore = new HashMap<>();
        do {
            line = scan.nextLine();
            String[] tuple = line.split(" ");
            String qid = tuple[0].trim();
            if(!qid.equals(currId)) {  // finish caching for a query
                if(currId != "-1")
                    this.rel.put(currId, relScore);
                currId = qid;
                relScore = new HashMap<>();
            }
            relScore.put(tuple[2].trim(), tuple[3].trim());
        } while(scan.hasNext());

        if (currId != "-1")
            this.rel.put(currId, relScore);

        scan.close();
    }

    public void cacheAvgDocLen() throws IOException {
        String[] fields = new String[] {"body", "title", "url", "inlink", "keywords"};
        for(String field: fields){
            double N_field = Idx.getDocCount(field);  // number of documents in the field
            double avg_docLen = Idx.getSumOfFieldLengths(field) / N_field;
            this.avgDocLen.put(field, avg_docLen);
        }
    }

    /**
     *  Run svm_rank_learn from within Java to train the model.
     *  @throws Exception Error accessing the Lucene index.
     */
    public void TrainRankSVM() throws Exception {
        String execPath = this.param.get("letor:svmRankLearnPath");
        String FEAT_GEN_c = this.param.get("letor:svmRankParamC");
        String qrelsFeatureOutputFile = this.param.get("letor:trainingFeatureVectorsFile");
        String modelOutputFile = this.param.get("letor:svmRankModelFile");
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { execPath, "-c", String.valueOf(FEAT_GEN_c), qrelsFeatureOutputFile,
                        modelOutputFile });

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null)
            System.out.println(line);

        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null)
            System.out.println(line);

        // get the return value from the executable. 0 means success, non-zero indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0)
            throw new Exception("SVM Rank crashed.");
    }

    /**
     *  Run svm_rank_classify from within Java to fetch scores for documents.
     *  @throws Exception Error accessing the Lucene index.
     */
    public void TestRankSVM() throws Exception {
        String execPath = this.param.get("letor:svmRankClassifyPath");
        String qrelsFeatureOutputFile = this.param.get("letor:testingFeatureVectorsFile");
        String modelInputFile = this.param.get("letor:svmRankModelFile");
        String predictions = this.param.get("letor:testingDocumentScores");
        Process cmdProc = Runtime.getRuntime().exec(
                new String[] { execPath, qrelsFeatureOutputFile,
                        modelInputFile, predictions});

        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null)
            System.out.println(line);

        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null)
            System.out.println(line);

        // get the return value from the executable. 0 means success, non-zero indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0)
            throw new Exception("SVM Rank crashed.");
    }

    /**
     *  re-rank the documents according to the SVM output
     *  @throws IOException Error accessing the file.
     */
    public void reRank() throws IOException {

        // read the original ranking order from the file
        BufferedReader input = null;
        // a hashmap storing inital. [ExternalId, (featureIdx, featureVal)]
        HashMap<String, ArrayList<>> Qryfeatures = new HashMap<>();

        try {
            String file = this.param.get("letor:testingFeatureVectorsFile");
            input = new BufferedReader(new FileReader(file));
            String line = null;

            //  Each pass of the loop processes one ranking record.
            while((line = input.readLine()) != null) {

                String[] tuple = line.split(" ");
                String qid = tuple[0].trim();
                String extId = tuple[2].trim();



                // compute features for the relevant docs of this query
                HashMap<String, String> relDocs = this.rel.get(qid);
                for(Map.Entry<String, String> idScore: relDocs.entrySet()){
                    String extId = idScore.getKey();
                    HashMap<Integer, Double> temp = computeFeatures(terms, extId);
                    Qryfeatures.put(extId, temp);
                }

                // normalize features and write to file
                String outFile = (genInitRanking? this.param.get("letor:testingFeatureVectorsFile"):
                        this.param.get("letor:trainingFeatureVectorsFile"));
                printNormFeatures(qid, Qryfeatures, outFile);
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        input.close();

        // initialize output file
        String file = this.param.get("trecEvalOutputPath");
        PrintWriter writer = new PrintWriter(new FileWriter(file, true));
    }

    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @throws IOException Error accessing the Lucene index.
     */
    public void processQueryFile(String queryFilePath, boolean genInitRanking) throws Exception {

        // generate testing data for top 100 documents in initial BM25 ranking
        if(genInitRanking) {
            RetrievalModelBM25 BM25 = new RetrievalModelBM25();
            BM25.setParameters(this.param);
            QryEval.processQueryFile(queryFilePath, BM25);
            cacheRelevance(this.param.get("trecEvalOutputPath"));
        }

        BufferedReader input = null;

        try {
            String line = null;
            input = new BufferedReader(new FileReader(queryFilePath));

            //  Each pass of the loop processes one query.
            while((line = input.readLine()) != null) {
                int d = line.indexOf(':');
                if(d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error: Missing ':' in query line.");
                }

                String qid = line.substring(0, d);
                String query = line.substring(d + 1);
                String[] terms = QryParser.tokenizeString(query);

                // a hashmap storing features for all relevant docs. [ExternalId, (featureIdx, featureVal)]
                HashMap<String, HashMap<Integer, Double>> Qryfeatures = new HashMap<>();

                // compute features for the relevant docs of this query
                HashMap<String, String> relDocs = this.rel.get(qid);
                for(Map.Entry<String, String> idScore: relDocs.entrySet()){
                    String extId = idScore.getKey();
                    HashMap<Integer, Double> temp = computeFeatures(terms, extId);
                    Qryfeatures.put(extId, temp);
                }

                // normalize features and write to file
                String outFile = (genInitRanking? this.param.get("letor:testingFeatureVectorsFile"):
                        this.param.get("letor:trainingFeatureVectorsFile"));
                printNormFeatures(qid, Qryfeatures, outFile);
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        input.close();
    }

    /**
     *  Compute feature vector for a query.
     *  @param extId external id of the document.
     *  @param terms An array of tokenize query terms.
     *  @throws IOException Error accessing the Lucene index.
     */
    public HashMap<Integer, Double> computeFeatures(String[] terms, String extId) throws Exception {
        int docid;
        try{ docid = Idx.getInternalDocid(extId);}
        catch(Exception e) {return null;}

        HashMap<Integer, Double> features = new HashMap<>();   // empty feature vector

        // f1: Spam score for d
        if(this.featureIdx.contains(1)){
            try{
                int spamScore = Integer.parseInt(Idx.getAttribute("score", docid));
                features.put(1, spamScore * 1.0);
            } catch(Exception e) {
                features.put(1, -1.0);
            }  // marker of invalid score
        }

        // f2: Url depth for d(number of '/' in the rawUrl field)
        if(this.featureIdx.contains(2)){
            try {
                String rawUrl = Idx.getAttribute("rawUrl", docid);
                int num = rawUrl.replaceAll("[^/]", "").length();
                features.put(2, num * 1.0 - 2);   // subtract the prefix "http://"
            } catch(Exception e) {
                features.put(2, -1.0);
            }  // marker of invalid score
        }

        // f3: FromWikipedia score for d (1 if rawUrl contains "wikipedia.org", o/w 0)
        if(this.featureIdx.contains(3)) {
            try {
                String rawUrl = Idx.getAttribute("rawUrl", docid);
                boolean fromWiki = rawUrl.contains("wikipedia.org");
                features.put(3, fromWiki ? 1.0 : 0.0);
            } catch (Exception e) {
                features.put(3, -1.0);
            }  // marker of invalid score
        }

        // f4: PageRank score for d (read from file).
        if(this.featureIdx.contains(4)) {
            if(this.pagerank.containsKey(extId))
                features.put(4, this.pagerank.get(extId));
            else features.put(4, -1.0);  // marker of invalid score
        }

        // f5: BM25 score for <q, d(body)>.
        if(this.featureIdx.contains(5))
            features.put(5, getBM25(terms, docid, "body"));

        // f6: Indri score for <q, dbody>.
        if(this.featureIdx.contains(6))
            features.put(6, getIndri(terms, docid, "body"));

        // f7: Term overlap score for <q, dbody>
        if(this.featureIdx.contains(7))
            features.put(7, getTermOverlap(terms, docid, "body"));

        // f8: BM25 score for <q, dtitle>.
        if(this.featureIdx.contains(8))
            features.put(8, getBM25(terms, docid, "title"));

        // f9: Indri score for <q, dtitle>.
        if(this.featureIdx.contains(9))
            features.put(9, getIndri(terms, docid, "title"));

        // f10: Term overlap score for <q, dtitle>.
        if(this.featureIdx.contains(10))
            features.put(10, getTermOverlap(terms, docid, "title"));

        // f11: BM25 score for <q, durl>.
        if(this.featureIdx.contains(11))
            features.put(11, getBM25(terms, docid, "url"));

        // f12: Indri score for <q, durl>.
        if(this.featureIdx.contains(12))
            features.put(12, getIndri(terms, docid, "url"));

        // f13: Term overlap score for <q, durl>.
        if(this.featureIdx.contains(13))
            features.put(13, getTermOverlap(terms, docid, "url"));

        // f14: BM25 score for <q, dinlink>.
        if(this.featureIdx.contains(14))
            features.put(14, getBM25(terms, docid, "inlink"));

        // f15: Indri score for <q, dinlink>.
        if(this.featureIdx.contains(15))
            features.put(15, getIndri(terms, docid, "inlink"));

        // f16: Term overlap score for <q, dinlink>.
        if(this.featureIdx.contains(16))
            features.put(16, getTermOverlap(terms, docid, "inlink"));

//        // f17: A custom feature - use your imagination.
//
//        // f18: A custom feature - use your imagination.

        return features;
    }

    /**
     *  Compute BM25 score for a document in a specific field.
     *  @param docid external id of the document.
     *  @param terms An array of tokenize query terms.
     *  @param field specified field of the document.
     *  @throws IOException Error accessing the Lucene index.
     */
    public double getBM25(String[] terms, int docid, String field) throws IOException{
        double score = 0.0;
        TermVector vec = new TermVector(docid, field);
        if(vec.stemsLength() == 0) return -1.0;  // document doesn't have the specified filed
        double docLen = Idx.getFieldLength(field, docid);

        for(String term: terms) {
            // check whether the term exists in the document
            int TermIdx = vec.indexOfStem(term);

            if(TermIdx != -1){    // term exists
                // Compute the RSJ (idf) weight of Okapi BMxx model
                double df = vec.stemDf(TermIdx);
                // Bug: N in IDF is different from N_field in avg_docLen
                // restrict RSJ weight to be non-negative
                double idfWeight = Math.max(0.0, Math.log((this.numOfDocs - df + 0.5) / (df + 0.5)));

                // Compute the tf weight of Okapi BMxx model
                double tf = vec.stemFreq(TermIdx);
                double tfWeight = tf / (tf + this.k1 * (1.0 - this.b + this.b * docLen / this.avgDocLen.get(field)));

                // Final BM25 score for this term in a specific doc
                score += idfWeight * tfWeight;  // Assumption: userWeight is always 1
            }
        }
        return score;
    }

    /**
     *  Compute Indri score for a document in a specific field.
     *  @param docid external id of the document.
     *  @param terms An array of tokenize query terms.
     *  @param field specified field of the document.
     *  @throws IOException Error accessing the Lucene index.
     */
    public double getIndri(String[] terms, int docid, String field) throws IOException{
        double score = 1.0;
        TermVector vec = new TermVector(docid, field);
        if(vec.stemsLength() == 0) return -1.0;  // document doesn't have the specified filed

        double lenCorpus = Idx.getSumOfFieldLengths(field);
        double docLen = Idx.getFieldLength(field, docid);
        int missTerms = 0;   // number of missed query terms in the field

        for(String term: terms) {
            double ctf = Idx.getTotalTermFreq(field, term);
            double p = ctf / lenCorpus;          // MLE of Prob(term in the collection)

            // check whether the term exists in the document
            int TermIdx = vec.indexOfStem(term);

            if(TermIdx != -1){    // term exists
                double tf = vec.stemFreq(TermIdx);
                score *= (1 - this.lambda) * (tf + this.mu * p) / (docLen + this.mu) + this.lambda * p;
            }
            else{   // term doesn't exist, call default score
                missTerms++;
                score *= (1 - this.lambda) * this.mu * p / (docLen + this.mu) + this.lambda * p;
            }
        }
        if(missTerms != terms.length) // check whether there's at least one query term match
            score = Math.pow(score, 1.0 / terms.length);
        else score = 0.0;

        return score;
    }

    /**
     *  Compute term overlap percentage for a document in a specific field.
     *  @param docid external id of the document.
     *  @param terms An array of tokenize query terms.
     *  @param field specified field of the document.
     *  @throws IOException Error accessing the Lucene index.
     */
    public double getTermOverlap(String[] terms, int docid, String field) throws IOException{
        TermVector vec = new TermVector(docid, field);
        if(vec.stemsLength() == 0) return -1.0;  // document doesn't have the specified filed
        double overlap = 0.0;

        for(String term: terms) {
            // check whether the term exists in the document
            int TermIdx = vec.indexOfStem(term);
            if(TermIdx != -1) overlap += 1;  // term exists
        }
        return overlap / terms.length;
    }

    /**
     *  Normalize feature vector for a query and write to file.
     *  @param qid query id.
     *  @param Qryfeatures features of documents corresponding to a query.
     *  @throws Exception Error accessing the Lucene index.
     */
    public void printNormFeatures(String qid, HashMap<String,
            HashMap<Integer, Double>> Qryfeatures, String file) throws Exception {

        // initialize output file
        FileWriter f = new FileWriter(file, true);
        PrintWriter writer = new PrintWriter(f);

        // initialize maximal and minimum value of each feature
        HashMap<Integer, Double> minVal = new HashMap<>(), maxVal = new HashMap<>();
        for(int i: this.featureIdx){
            minVal.put(i, Double.MAX_VALUE);
            maxVal.put(i, Double.MIN_VALUE);
        }

        // find maximal and minimum value of each feature
        for(Map.Entry<String, HashMap<Integer, Double>> docFeature: Qryfeatures.entrySet()) {
            HashMap<Integer, Double> features = docFeature.getValue();
            if(features == null) continue;          // invalid document

//            for(Map.Entry<Integer, Double> e: features.entrySet())
//                System.out.println(String.format("%d, %f", e.getKey(), e.getValue()));

            for(int i: this.featureIdx){
//                System.out.println(i);
                double val = features.get(i);
                if(val != -1) {                     // valid feature
                    minVal.put(i, Math.min(minVal.get(i), val));
                    maxVal.put(i, Math.max(maxVal.get(i), val));
                }
            }
        }

        // denominator (max - min) for minMax normalization
        HashMap<Integer, Double> valDiff = new HashMap<>();
        for(int i: this.featureIdx)
            valDiff.put(i, maxVal.get(i) - minVal.get(i));

        // normalize feature values to [0, 1] and write to file
        for(Map.Entry<String, HashMap<Integer, Double>> docFeature: Qryfeatures.entrySet()) {
            HashMap<Integer, Double> features = docFeature.getValue();
            if(features == null) continue;          // invalid document outside the corpora
            String extId = docFeature.getKey();
            String relScore = this.rel.get(qid).get(extId);
            String output = String.format("%s qid:%s ", relScore, qid);

            for(int i: this.featureIdx) {
                double val = features.get(i);
                double diff = valDiff.get(i);
                if((val != -1) && (diff != 0))      // valid feature
                    output += String.format("%d:%f ", i, (val - minVal.get(i)) / diff);
                else
                    output += String.format("%d:%f ", i, 0.0);
            }
            output += String.format("# %s", extId);
            System.out.println(output);
            writer.println(output);
        }
        writer.close();
    }

}
