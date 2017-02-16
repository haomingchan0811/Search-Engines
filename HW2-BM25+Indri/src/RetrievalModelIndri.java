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

    private int mu;
    private double lambda;

	public String defaultQrySopName() {
		return new String("#and");
	}

    // set parameters for retrieval model
    public void setParameters(Map<String, String> param) {

        mu = Integer.parseInt(param.get("Indri:mu"));
        if(mu < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:mu") + ", mu is an integer >= 0");

        lambda = Double.parseDouble(param.get("Indri:lambda"));
        if(lambda < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("Indri:lambda") + ", lambda is a real number between 0.0 and 1.0");
    }

}
