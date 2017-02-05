/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop{

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch(RetrievalModel r){
	  return this.docIteratorHasMatchFirst(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore(RetrievalModel r) throws IOException{

    if(r instanceof RetrievalModelUnrankedBoolean){
    	return this.getScoreUnrankedBoolean(r);
    }
    else if(r instanceof RetrievalModelRankedBoolean){
        return this.getScoreRankedBoolean(r);
    }
    else{
    	throw new IllegalArgumentException
    	(r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean(RetrievalModel r) throws IOException{
	  if(this.docIteratorHasMatchCache()){
		  // return the term frequency as the score 
		  return this.getArg(0).docIteratorGetMatchPosting().tf;
	  }
	  else return 0.0;
  }

  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean(RetrievalModel r) throws IOException{
	  if(!this.docIteratorHasMatchCache())
		  return 0.0;
	  else
		  return 1.0;
  }

  /**
   *  Initialize the query operator(and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize(RetrievalModel r) throws IOException{
	  Qry q = this.args.get(0);
	  q.initialize(r);
  }

}
