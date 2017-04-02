/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  An object that stores parameters for the ranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelRankedBoolean extends RetrievalModel {

//	public String defaultQrySopName() {
//		return new String("#or");
//	}

	// change of default operator for HW3-query expansion as a baseline for comparison
	public String defaultQrySopName() {
		return new String("#and");
	}

	public void setParameters(Map<String, String> param){ return;}
}
