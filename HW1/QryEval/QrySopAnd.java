/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch(RetrievalModel r) {
	  return this.docIteratorHasMatchAll(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

	if(r instanceof RetrievalModelUnrankedBoolean) {
		return this.getScoreUnrankedBoolean(r);
	} 
	else if(r instanceof RetrievalModelRankedBoolean){
		return this.getScoreRankedBoolean(r);
	}
	else{
		throw new IllegalArgumentException
	    (r.getClass().getName() + " doesn't support the AND operator.");
	}
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
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
		  
		  // Initialize the score as the maximal integer
		  double score = Integer.MAX_VALUE;
		 
		  /* Return the maximum score of all query arguments
		   * Note that AND uses docIteratorHasMatchAll where
		   * docids are already matched, no need to check 
		   */
		  for(int i = 0; i < this.args.size(); i++){
			  QrySop q_i = (QrySop)(this.args.get(i));
			  score = Math.min(score, q_i.getScore(r));
//			  if(q_i instanceof QrySopScore)
//				  score = ((QrySopScore) q_i).getScore(r);
//			  else if(q_i instanceof QrySopOr)
//				  score = ((QrySopOr) q_i).getScore(r);
//			  else if(q_i instanceof QrySopAnd)
//				  score = ((QrySopAnd) q_i).getScore(r);
		  }
		  return score;
	  }
	  else
		  return 0.0;
  }
}
