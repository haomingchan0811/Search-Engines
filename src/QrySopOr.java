/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch(RetrievalModel r) {
		return this.docIteratorHasMatchMin(r);
	}

	/**
	 *  Get a score for the document that docIteratorHasMatch matched.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
  	public double getScore(RetrievalModel r) throws IOException {
    
  		if(r instanceof RetrievalModelUnrankedBoolean)
  			return this.getScoreUnrankedBoolean(r);
  		else if(r instanceof RetrievalModelRankedBoolean)
  			return this.getScoreRankedBoolean(r);
  		else{
  			throw new IllegalArgumentException
  			(r.getClass().getName() + " doesn't support the OR operator.");
  		}
  	}
  
  	/**
  	 *  getScore for the UnrankedBoolean retrieval model.
  	 *  @param r The retrieval model that determines how scores are calculated.
  	 *  @return The document score.
  	 *  @throws IOException Error accessing the Lucene index
  	 */
  	private double getScoreUnrankedBoolean(RetrievalModel r) throws IOException {
  		
  		if(!this.docIteratorHasMatchCache())
  			return 0.0;
  		else 
  			return 1.0;
  	}
  
  /**
   *  getScore for the RankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
	  
	  if(this.docIteratorHasMatchCache()) {
		  
		  double score = 0.0;
		  
		  // Current matched document id 
		  int docid = this.docIteratorGetMatch();
		  
		  /* Return the maximum score of all query arguments 
		   * that matches the current docid.
		   */
		  for(int i = 0; i < this.args.size(); i++){
			  QrySop q_i = (QrySop) this.args.get(i);
			  
			  // BUG: forget to check whether it has a match or whether docid matches 
			  if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid)
				  score = Math.max(score, q_i.getScore(r));			  
		  }
		  return score;
	  }
	  else
		  return 0.0;
  }

}
