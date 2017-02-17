/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *  The Window operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {

	//  The distance constraint between arguments
	private int distance;

	public QryIopWindow(String distance){
		this.distance = Integer.parseInt(distance);
	}

	/**
	 *  Evaluate the query operator; the result is an internal inverted
	 *  list that may be accessed via the internal iterators.
	 *  @throws IOException Error accessing the Lucene index.
	 *  @throws IllegalArgumentException invalid number of arguments for the NEAR operator
	 */
	protected void evaluate()throws IOException {

	    //  Create an empty inverted list. If there are no query arguments,
	    //  that's the final result.
	    
	    this.invertedList = new InvList(this.getField());
	    if(args.size() == 0) return;

	    // Each pass of the loop adds 1 document to result inverted list
	    // until all of the argument inverted lists are depleted.
	    while(true) {
	    	// First, find the next document id where all arguments exist 
	    	// If there is none, we're done.
	    	
	    	boolean docMatchFound = false;
	
		    // Keep trying until all matches are found or no match is possible.
		    while(!docMatchFound) {
		
	    		// Get the docid of the first query argument.
	    		Qry q_0 = this.args.get(0);
	    		if(!q_0.docIteratorHasMatch(null)) return;   // first argument exhausted
	    		int docid_0 = q_0.docIteratorGetMatch();
		
	    		// Other query arguments must match the docid of the first query argument.
	    		docMatchFound = true;
		
	    		for(int i = 1; i < this.args.size(); i++) {
	    			Qry q_i = this.args.get(i);
	    			q_i.docIteratorAdvanceTo(docid_0);
	    			
	    			if(!q_i.docIteratorHasMatch(null)) 		// If any argument is exhausted
	    				return;								// there are no more matches.
		
	    			int docid_i = q_i.docIteratorGetMatch();
	    			
	    			if(docid_0 != docid_i) {					// docid_0 can't match. Try again.
	    				q_0.docIteratorAdvanceTo(docid_i);
//	    				System.out.println(q_0.docIteratorGetMatch());
	    				docMatchFound = false;
	    				break;
	    			}
	    		}
	    		
	    		// Secondly, find the positions that satisfy the constraint in the same doc
	    		if(docMatchFound) {
	    			// Create a new posting to record the location information				
				   	List<Integer> positions = new ArrayList<Integer>();
				   	
	    			boolean moreLoc = true; // whether there're more locations to search			
//	    	    	System.out.println("found a document");
	    			while(moreLoc) {	    			
//		    	    	System.out.println("looking for positions of doc" + docid_0);
		    	    	
				    	boolean locMatchFound = false;
				    	int prevElemLoc = -1;
				    	
				    	// keep trying until a match is found or no match is possible
				    	while(!locMatchFound) {    	
		    	    		// Get the locid of the first query argument.
				    	 	QryIop loc_0 = (QryIop) q_0;
				    	 	
				    	 	// all locids have been processed. Done for this document
				    	 	if(!loc_0.locIteratorHasMatch()) {
//				    	 		System.out.println("1st elem exhausted!!");
				    	 		moreLoc = false; 
			    	 			locMatchFound = false;
				    	 		break;
				    	 	}  	
				    	 	int locid_0 = loc_0.locIteratorGetMatch();
// 			    	    	System.out.println("locations found: " + locid_0);
				    	 	
				    	 	locMatchFound = true;	// other positions must satisfy the proximity constraint
				    	
				    	 	prevElemLoc = locid_0;  // location of the previous element 
				    	
				    	 	for(int i = 1; i < this.args.size(); i++) {
				    	 		QryIop loc_i = (QryIop) this.args.get(i);
				    	 		
				    	 		loc_i.locIteratorAdvancePast(prevElemLoc);
	
				    	 		if(!loc_i.locIteratorHasMatch()) {
//					    	 		System.out.println("elem exhausted!!");
				    	 			moreLoc = false;
				    	 			prevElemLoc = -1;
					    	 		break; 			// locations exhausted. Done
				    	 		}
				    	 		int locid_i = loc_i.locIteratorGetMatch();
//				    	 		System.out.println("location for 2nd: " + locid_i);
				    	 		
				    	 		// location proximity doesn't match
				    	 		if(locid_i - prevElemLoc > distance) {
				    	 			loc_0.locIteratorAdvance();
				    	 			locMatchFound = false;
				    	 			break;
				    	 		}
//				    	 		System.out.println("successfully found a match seq!");
				    	 		prevElemLoc = locid_i;
				    	 	}
				    	}
				    	if(locMatchFound && prevElemLoc != -1) {
//			    	 		System.out.println("add into list: " + prevElemLoc);
				    		positions.add(prevElemLoc);  // add the matched location
				    		for(Qry q_i: this.args) {
				    			QryIop loc_i = (QryIop) q_i;
				    	 		loc_i.locIteratorAdvancePast(loc_i.locIteratorGetMatch());
				    		}
				    	}
	    			}
//	    			System.out.println(positions.size());
	    			if(positions.size() > 0) {
		    			Collections.sort(positions);
		    			this.invertedList.appendPosting(docid_0, positions);
	    			}
	    		}
	    		q_0.docIteratorAdvancePast(docid_0);
    		}		
		 }
	}

}
