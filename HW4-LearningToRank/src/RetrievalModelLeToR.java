/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 *  An object that stores parameters for the LearningToRanks
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelLeToR extends RetrievalModel {

    private Map<String, String> param;
    private HashMap<String, Double> pagerank = new HashMap<>(), avgDocLen = new HashMap<>();
    private HashMap<String, HashMap<String, String>> rel =
            new HashMap<String, HashMap<String, String>>();
    private long numOfDocs;     // global stats of corpora
    private double k1, b, k3;   // parameters for BM25 model
    private double mu, lambda;  // parameters for Indri model
    private int numOfFeatures;

    public String defaultQrySopName() {
		return null;
	}

	// set parameters for retrieval model
	public void setParameters(Map<String, String> parameters) {
	    this.param = parameters;
	    this.numOfFeatures = 16;

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

        String trainFile = this.param.get("letor:trainingQueryFile");
        processQueryFile(trainFile);
//        String testFile = param.get("letor:queryFilePath");
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
     *  Process the query file.
     *  @param queryFilePath
     *  @throws IOException Error accessing the Lucene index.
     */
    public void processQueryFile(String queryFilePath) throws Exception {

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
                // a hashmap storing features for all relevant docs.
                HashMap<String, ArrayList<Double>> Qryfeatures = new HashMap<>();

                // compute features for the relevant docs of this query
                HashMap<String, String> relDocs = this.rel.get(qid);
                for(Map.Entry<String, String> idScore: relDocs.entrySet()){
                    String extId = idScore.getKey();
                    ArrayList<Double> temp = computeFeatures(terms, extId);
                    Qryfeatures.put(extId, temp);
                }

                // normalize features and write to file
                printNormFeatures(qid, Qryfeatures);


//                System.out.println("!!!!!!");
            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        input.close();
    }

    /**
     *  Normalize feature vector for a query and write to file.
     *  @param qid query id.
     *  @param Qryfeatures features of documents corresponding to a query.
     *  @throws Exception Error accessing the Lucene index.
     */
    public void printNormFeatures(String qid, HashMap<String, ArrayList<Double>> Qryfeatures) throws Exception {

        // initialize output file
        FileWriter f = new FileWriter(this.param.get("letor:trainingFeatureVectorsFile"));
        PrintWriter writer = new PrintWriter(f, true);

        // initialize maximal and minimum value of each feature
        ArrayList<Double> minVal = new ArrayList<>(), maxVal = new ArrayList<>();
        for(int i = 0; i < this.numOfFeatures; i++){
            minVal.add(Double.MAX_VALUE);
            maxVal.add(Double.MIN_VALUE);
        }

        // find maximal and minimum value of each feature
        for(Map.Entry<String, ArrayList<Double>> docFeature: Qryfeatures.entrySet()) {
            ArrayList<Double> features = docFeature.getValue();
            if(features == null) continue;          // invalid document

            for(int i = 0; i < this.numOfFeatures; i++) {
                double val = features.get(i);
                if(val != -1) {                     // valid feature
                    minVal.set(i, Math.min(minVal.get(i), val));
                    maxVal.set(i, Math.max(minVal.get(i), val));
                }
            }
        }

        // denominator (max - min) for minMax normalization
        ArrayList<Double> valDiff = new ArrayList<>();
        for(int i = 0; i < this.numOfFeatures; i++)
            valDiff.add(maxVal.get(i) - minVal.get(i));

        // normalize feature values to [0, 1] and write to file
        for(Map.Entry<String, ArrayList<Double>> docFeature: Qryfeatures.entrySet()) {
            ArrayList<Double> features = docFeature.getValue();
            if(features == null) continue;          // invalid document
            String extId = docFeature.getKey();
            String relScore = this.rel.get(qid).get(extId);
            String output = String.format("%s qid:%s ", relScore, qid);

            for(int i = 0; i < this.numOfFeatures; i++) {
                double val = features.get(i);
                double diff = valDiff.get(i);
                if((val != -1) && (diff != 0))      // valid feature
                    output += String.format("%d:%f ", i + 1, (val - minVal.get(i)) / diff);
                else
                    output += String.format("%d:%f ", i + 1, 0.0);
            }
            output += String.format("# %s", extId);
            System.out.println(output);
            writer.println(output);
        }
        writer.close();
    }

    /**
     *  Compute feature vector for a query.
     *  @param extId external id of the document.
     *  @param terms An array of tokenize query terms.
     *  @throws IOException Error accessing the Lucene index.
     */
    public ArrayList<Double> computeFeatures(String[] terms, String extId) throws Exception {
        int docid;
        try{ docid = Idx.getInternalDocid(extId);}
        catch(Exception e) {return null;}

        ArrayList<Double> features = new ArrayList<>();   // empty feature vector

        // f1: Spam score for d
        try{
            int spamScore = Integer.parseInt(Idx.getAttribute("score", docid));
            features.add(spamScore * 1.0);
        }
        catch(Exception e) { features.add(-1.0);}  // marker of invalid score


        // f2: Url depth for d(number of '/' in the rawUrl field)
        try{
            String rawUrl = Idx.getAttribute("rawUrl", docid);
            int num = rawUrl.replaceAll("[^/]", "").length();
            features.add(num * 1.0 - 2);   // subtract the prefix "http://"
        }
        catch(Exception e) { features.add(-1.0);}  // marker of invalid score

        // f3: FromWikipedia score for d (1 if rawUrl contains "wikipedia.org", o/w 0)
        try{
            String rawUrl = Idx.getAttribute("rawUrl", docid);
            boolean fromWiki = rawUrl.contains("wikipedia.org");
            features.add(fromWiki? 1.0: 0.0);
        }
        catch(Exception e) { features.add(-1.0);}  // marker of invalid score

        // f4: PageRank score for d (read from file).
        try{ features.add(this.pagerank.get(extId));}
        catch(Exception e) { features.add(-1.0);}  // marker of invalid score

        // f5: BM25 score for <q, d(body)>.
        features.add(getBM25(terms, docid, "body"));

//        // f6: Indri score for <q, dbody>.
        features.add(getIndri(terms, docid, "body"));

        // f7: Term overlap score for <q, dbody>
        features.add(getTermOverlap(terms, docid, "body"));

        // f8: BM25 score for <q, dtitle>.
        features.add(getBM25(terms, docid, "title"));

        // f9: Indri score for <q, dtitle>.
        features.add(getIndri(terms, docid, "title"));

        // f10: Term overlap score for <q, dtitle>.
        features.add(getTermOverlap(terms, docid, "title"));

        // f11: BM25 score for <q, durl>.
        features.add(getBM25(terms, docid, "url"));

        // f12: Indri score for <q, durl>.
        features.add(getIndri(terms, docid, "url"));

        // f13: Term overlap score for <q, durl>.
        features.add(getTermOverlap(terms, docid, "url"));

        // f14: BM25 score for <q, dinlink>.
        features.add(getBM25(terms, docid, "inlink"));

        // f15: Indri score for <q, dinlink>.
        features.add(getIndri(terms, docid, "inlink"));

        // f16: Term overlap score for <q, dinlink>.
        features.add(getTermOverlap(terms, docid, "inlink"));

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

}
