/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 )*/
public class RetrievalModelIndri extends RetrievalModel {

    private double mu, lambda;

	public String defaultQrySopName() {
		return new String("#and");
	}

    // set parameters for retrieval model
    public void setParameters(Map<String, String> param) {

        // check the occurrence of required parameters
        if(!(param.containsKey("Indri:mu") && param.containsKey("Indri:lambda"))){
            throw new IllegalArgumentException
                    ("Required parameters for Indri model were missing from the parameter file.");
        }

        mu = Integer.parseInt(param.get("Indri:mu"));
        if(mu < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:mu") + ", mu is an integer >= 0");

        lambda = Double.parseDouble(param.get("Indri:lambda"));
        if(lambda < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:lambda") + ", lambda is a real number between 0.0 and 1.0");
    }

    // Fetch value of the parameter
    public double getParam(String s) {
        switch(s) {
            case "mu":
                return this.mu;
            case "lambda":
                return this.lambda;
            default:
                throw new IllegalArgumentException
                        ("Illegal argument: Indri doesn't have argument " + s);
        }
    }
}
