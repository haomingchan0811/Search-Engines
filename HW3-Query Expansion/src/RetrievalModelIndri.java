/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import com.sun.javafx.binding.StringFormatter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 )*/
public class RetrievalModelIndri extends RetrievalModel {

    private double mu, lambda, fbOrigWeight;
    private int fbDocs, fbTerms, fbMu;
    private String fb = "false", fbInitialRankingFile = "", fbExpansionQueryFile = "";
    private Map<Integer, ScoreList> initialRanking;

    public String defaultQrySopName() {
        return new String("#and");
    }

    // set parameters for retrieval model
    public void setParameters(Map<String, String> param) {

        // check the occurrence of required parameters
        if(!(param.containsKey("Indri:mu") && param.containsKey("Indri:lambda"))){
            throw new IllegalArgumentException
                    ("Required parameters for IndriExpansion model were missing from the parameter file.");
        }

        mu = Double.parseDouble(param.get("Indri:mu"));
        if(mu < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:mu") + ", mu is a real number >= 0");

        lambda = Double.parseDouble(param.get("Indri:lambda"));
        if(lambda < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:lambda") + ", lambda is a real number between 0.0 and 1.0");

        if((param.containsKey("fb") && param.get("fb").equals("true"))) {
            fb = "true";

            fbDocs = Integer.parseInt(param.get("fbDocs"));
            if (fbDocs <= 0) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("fbDocs") + ", fbDocs is an integer > 0");

            fbTerms = Integer.parseInt(param.get("fbTerms"));
            if (fbTerms <= 0) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("fbTerms") + ", fbTerms is an integer > 0");

            fbMu = Integer.parseInt(param.get("fbMu"));
            if (fbMu < 0) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("fbMu") + ", fbMu is an integer >= 0");

            fbOrigWeight = Double.parseDouble(param.get("fbOrigWeight"));
            if (fbOrigWeight < 0) throw new IllegalArgumentException
                    ("Illegal argument: " + param.get("fbOrigWeight") + ", fbOrigWeight is a real number between 0.0 and 1.0");

            if (param.containsKey("fbInitialRankingFile") && param.get("fbInitialRankingFile") != "") {
                fbInitialRankingFile = param.get("fbInitialRankingFile");
                try{
                    initialRanking = readRanking(fbInitialRankingFile);
                }
                catch (Exception e){
                    e.printStackTrace();
                }
            }

            if (param.containsKey("fbExpansionQueryFile") && param.get("fbExpansionQueryFile") != "")
                fbExpansionQueryFile = param.get("fbExpansionQueryFile");
        }
    }

    // Fetch value of the parameter
    public double getParam(String s) {
        switch(s) {
            case "mu":
                return this.mu;
            case "lambda":
                return this.lambda;
            case "fbDocs":
                return this.fbDocs;
            case "fbTerms":
                return this.fbTerms;
            case "fbMu":
                return this.fbMu;
            case "fbOrigWeight":
                return this.fbOrigWeight;
            default:
                throw new IllegalArgumentException
                        ("Illegal argument: IndriExpansion doesn't have argument " + s);
        }
    }

    // Fetch value of the file path
    public String getFilePath(String s) {
        switch(s) {
            case "fb":
                return this.fb;
            case "fbInitialRankingFile":
                return this.fbInitialRankingFile;
            case "fbExpansionQueryFile":
                return this.fbExpansionQueryFile;
            default:
                throw new IllegalArgumentException
                        ("Illegal argument: IndriExpansion doesn't have argument " + s);
        }
    }


    /**
     *  Read the specified ranking file.
     *  @return The candidate query terms.
     */
    private static Map<Integer, ScoreList> readRanking(String file) throws Exception {

        Map<Integer, ScoreList> output = new HashMap<>();
        ScoreList scores = new ScoreList();
        File rankingFile = new File(file);
        int prevId = -1;

        if(!rankingFile.canRead())
            throw new IllegalArgumentException("Can't read " + file);

        Scanner scan = new Scanner(rankingFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] tuple = line.split(" ");
            int qid = Integer.parseInt(tuple[0]);

            // initialize a ScoreList for a new query
            if(qid != prevId) {

                if(scores.size() != 0)
                    output.put(prevId, scores);

                scores = new ScoreList();
                prevId = qid;
            }
//            System.out.println(String.format("qid: %d, docid: %d, score: %.11f", qid, Idx.getInternalDocid(tuple[2]), Double.parseDouble(tuple[4])));
            scores.add(Idx.getInternalDocid(tuple[2]), Double.parseDouble(tuple[4]));
        } while(scan.hasNext());

        scan.close();
        output.put(prevId, scores);
        return output;
    }
}
