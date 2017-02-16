/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

    private double k1, b, k3;

	public String defaultQrySopName() {
		return new String("#sum");
	}

	// set parameters for retrieval model
	public void setParameters(Map<String, String> param) {

	    // check the occurrence of required parameters
        if(!(param.containsKey("BM25:k_1") &&
                param.containsKey("BM25:k_3") &&
                param.containsKey("BM25:b"))) {
            throw new IllegalArgumentException
                    ("Required parameters for BM25 model were missing from the parameter file.");
        }

        k1 = Double.parseDouble(param.get("BM25:k_1"));
        if(k1 < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("BM25:k_1") + ", k1 is a real number >= 0.0");

        b = Double.parseDouble(param.get("BM25:b"));
        if(b < 0 || b > 1) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("BM25:b") + ", b is a real number between 0.0 and 1.0");

        k3 = Double.parseDouble(param.get("BM25:k_3"));
        if(k3 < 0) throw new IllegalArgumentException
                ("Illegal argument: " + param.get("BM25:k_3") + ", k3 is a real number >= 0.0");
    }

}
