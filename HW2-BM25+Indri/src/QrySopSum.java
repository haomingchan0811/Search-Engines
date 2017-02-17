/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The Sum operator for BM25 retrieval models.
 */
public class QrySopSum extends QrySop {

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
  	return this.getScoreBM25(r);

//	if(r instanceof RetrievalModelUnrankedBoolean) {
//		return this.getScoreUnrankedBoolean(r);
//	}
//	else if(r instanceof RetrievalModelRankedBoolean){
//		return this.getScoreRankedBoolean(r);
//	}
//	else{
//		throw new IllegalArgumentException
//	    (r.getClass().getName() + " doesn't support the AND operator.");
//	}
  }

    /**
     *  Get a default score for a document if docIteratorHasMatch doesn't matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @param docid The document id to compute the default score
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        return this.getScoreBM25(r);
    }

  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreBM25(RetrievalModel r) throws IOException {

      // Initialize the score as zero
      double score = 0.0;

      // Current matched document id
      int docid = this.docIteratorGetMatch();

      /* Return the sum of scores of all query arguments that
       * match the current docid.(SUM uses docIteratorHasMatchMin)
       */
      for(int i = 0; i < this.args.size(); i++){
          QrySop q_i = (QrySop) this.args.get(i);

          // check whether this specific argument exists in the current doc
          if(q_i.docIteratorHasMatch(r) && docid == q_i.docIteratorGetMatch())
              score += q_i.getScore(r);
      }
      return score;
  }

//  /**
//   *  getScore for the UnrankedBoolean retrieval model.
//   *  @param r The retrieval model that determines how scores are calculated.
//   *  @return The document score.
//   *  @throws IOException Error accessing the Lucene index
//   */
//  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
//    if(!this.docIteratorHasMatchCache())
//    		return 0.0;
//    else
//    		return 1.0;
//  }
//
//  /**
//   *  getScore for the RankedBoolean retrieval model.
//   *  @param r The retrieval model that determines how scores are calculated.
//   *  @return The document score.
//   *  @throws IOException Error accessing the Lucene index
//   */
//  private double getScoreRankedBoolean(RetrievalModel r) throws IOException {
//	  if(this.docIteratorHasMatchCache()) {
//
//		  // Initialize the score as the maximal integer
//		  double score = Integer.MAX_VALUE;
//
//		  /* Return the maximum score of all query arguments
//		   * Note that AND uses docIteratorHasMatchAll where
//		   * docids are already matched, no need to check
//		   */
//		  for(int i = 0; i < this.args.size(); i++){
//			  QrySop q_i = (QrySop) this.args.get(i);
//			  score = Math.min(score, q_i.getScore(r));
//		  }
//		  return score;
//	  }
//	  else
//		  return 0.0;
//  }
}
