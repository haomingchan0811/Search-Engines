/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 )*/
public class RetrievalModelIndriExpansion extends RetrievalModel {

    private double mu, lambda, fbOrigWeight;
//    private boolean fb;
    private int fbDocs, fbTerms, fbMu;
    private String fbInitialRankingFile = "", fbExpansionQueryFile = "";

	public String defaultQrySopName() {
		return new String("#and");
	}

    // set parameters for retrieval model
    public void setParameters(Map<String, String> param) {

        // check the occurrence of required parameters
        if(!(param.containsKey("Indri:mu") && param.containsKey("Indri:lambda")
                && param.containsKey("fb") && param.containsKey("fbDocs")
                && param.containsKey("fbTerms") && param.containsKey("fbMu"))){
            throw new IllegalArgumentException
                    ("Required parameters for IndriExpansion model were missing from the parameter file.");
        }

        mu = Double.parseDouble(param.get("Indri:mu"));
        if(mu < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:mu") + ", mu is a real number >= 0");

        lambda = Double.parseDouble(param.get("Indri:lambda"));
        if(lambda < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:lambda") + ", lambda is a real number between 0.0 and 1.0");

        fbDocs = Integer.parseInt(param.get("fbDocs"));
        if(fbDocs <= 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("fbDocs") + ", fbDocs is an integer > 0");

        fbTerms = Integer.parseInt(param.get("fbTerms"));
        if(fbTerms <= 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("fbTerms") + ", fbTerms is an integer > 0");

        fbMu = Integer.parseInt(param.get("fbMu"));
        if(fbMu < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("fbMu") + ", fbMu is an integer >= 0");

        fbOrigWeight = Double.parseDouble(param.get("fbOrigWeight"));
        if(fbOrigWeight < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("fbOrigWeight") + ", fbOrigWeight is a real number between 0.0 and 1.0");

        if(param.containsKey("fbInitialRankingFile") && param.get("fbInitialRankingFile") != "")
            fbInitialRankingFile = param.get("fbInitialRankingFile");

        if(param.containsKey("fbExpansionQueryFile") && param.get("fbExpansionQueryFile") != "")
            fbExpansionQueryFile = param.get("fbExpansionQueryFile");
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
            case "fbInitialRankingFile":
                return this.fbInitialRankingFile;
            case "fbExpansionQueryFile":
                return this.fbExpansionQueryFile;
            default:
                throw new IllegalArgumentException
                        ("Illegal argument: IndriExpansion doesn't have argument " + s);
        }
    }
}
