/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
    private HashMap<String, Double> pagerank = new HashMap<>();
    private HashMap<String, HashMap<String, String>> rel = new HashMap<String, HashMap<String, String>>();
    private String trainingQueryFile, testQueryFile;

	public String defaultQrySopName() {
		return null;
	}

	// set parameters for retrieval model
	public void setParameters(Map<String, String> parameters) {
        this.param = parameters;
    }

    // Fetch value of the parameter
    public double getParam(String s) {
//        switch (s) {
//            case "k1":
//                return this.k1;
//            case "b":
//                return this.b;
//            case "k3":
//                return this.k3;
//            default:
//                throw new IllegalArgumentException
//                        ("Illegal argument: BM25 doesn't have argument " + s);
//        }
        return 0;
    }

    public void learn(Map<String, String> parameters) throws IOException {
	    setParameters(parameters);
        cachePageRank(param.get("letor:pageRankFile"));          // cache (externalId, pageRank)
        cacheRelevance(param.get("letor:trainingQrelsFile"));    // cache [qid, (externalId, relScore)]

//        String trainFile = param.get("letor:trainingQueryFile");
//        processQueryFile(trainFile);
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


    /**
     *  Process the query file.
     *  @param queryFilePath
     *  @throws IOException Error accessing the Lucene index.
     */
    static void processQueryFile(String queryFilePath) throws IOException {

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

                int qid = Integer.parseInt(line.substring(0, d));
                String query = line.substring(d + 1);
                String[] terms = QryParser.tokenizeString(query);
//                computeFeatrue(qid, terms);


            }
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        input.close();
    }
}
