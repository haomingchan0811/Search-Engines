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
     *
	 *  Implementation:
     *  Consider #WINDOW/n(a b c). Iterates down the locations for a, b, c in parallel. Suppose the three iterators
     *  all start at the first location for each term. The window size that covers those 3 term occurrences is of size
     *  1 + Max(a.currentloc, b.currentloc, c.currentloc) - Min(a.currentloc, b.currentloc, c.currentloc). If the size
     *  is > N, advance the iterator that has the Min location. If the size is <= N, you have a match, and you advance
     *  all 3 iterators. Continue until any iterator reaches the end of its location list.
	 *
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

                    // whether there're more potential locations to match in this document
	    			while(moreLoc) {
//		    	    	System.out.println("looking for positions of doc" + docid_0);
		    	    	
				    	boolean locMatchFound = false;
                        int maxLoc = -1, minLoc = -1;    // initialize min & max location of all arguments
                        QryIop minLocIte = null;      // record the argument that has the smallest location

                        // keep trying until a match is found or no match is possible
				    	while(!locMatchFound) {    	
		    	    		// Get the locid of the first query argument.
				    	 	QryIop loc_0 = (QryIop) q_0;
				    	 	
				    	 	// all locids have been processed. Done for this document
				    	 	if(!loc_0.locIteratorHasMatch()) {
//				    	 		System.out.println("1st elem exhausted!!");
				    	 		moreLoc = false; 
				    	 		break;
				    	 	}
                            maxLoc = minLoc = loc_0.locIteratorGetMatch();  // use first argument's location
                            minLocIte = loc_0;
// 			    	    	System.out.println("locations found: " + loc_0);
				    	 	
				    	 	locMatchFound = true;	// other positions must satisfy the proximity constraint

				    	 	for(int i = 1; i < this.args.size(); i++) {
                                QryIop loc_i = (QryIop) this.args.get(i);

                                if (!loc_i.locIteratorHasMatch()) {
//                                    System.out.println((i + 1) + "th elem exhausted!!");
                                    moreLoc = false;
                                    maxLoc = -1;       // mark that element exhausted
                                    break;             // locations exhausted for this argument. Done
                                }
                                int locid_i = loc_i.locIteratorGetMatch();
                                maxLoc = Math.max(locid_i, maxLoc);
                                if (minLoc > locid_i) {
                                    minLoc = locid_i;
                                    minLocIte = loc_i;
                                }
//                                System.out.println("location for 2nd: " + locid_i);
                            }
				    	}
                        if(locMatchFound && maxLoc != -1){
                            // location window constraint doesn't match
                            if(maxLoc - minLoc + 1 > distance) {
//                                System.out.println(String.format("minLoc=%d, maxLoc=%d, window=%d", minLoc, maxLoc, distance));
//                                System.out.println("failed to satisfy window, move on");
                                minLocIte.locIteratorAdvance();
                            }
                            else{
//                                System.out.println("successfully found a match seq!");
//                                System.out.println("add into list: " + maxLoc);
                                positions.add(maxLoc);  // add the matched location
                                for(Qry q_i: this.args) {
                                    QryIop loc_i = (QryIop) q_i;
                                    loc_i.locIteratorAdvancePast(loc_i.locIteratorGetMatch());
                                }
                            }
                        }

	    			}
//	    			System.out.println(String.format("Found %d locations", positions.size()));
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
