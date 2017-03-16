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
      if(r instanceof RetrievalModelIndri || r instanceof RetrievalModelIndriExpansion)
          return this.docIteratorHasMatchMin(r);
      else
          return this.docIteratorHasMatchAll(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore(RetrievalModel r) throws IOException {

    if(r instanceof RetrievalModelUnrankedBoolean) {
        return this.getScoreUnrankedBoolean(r);
    }
    else if(r instanceof RetrievalModelRankedBoolean){
        return this.getScoreRankedBoolean(r);
    }
    else if(r instanceof RetrievalModelIndri || r instanceof RetrievalModelIndriExpansion){
        return this.getScoreIndri(r);
    }
    else{
        throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }

    /**
     *  Get a default score for a document if docIteratorHasMatch doesn't matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @param docid The document id to compute the default score
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        double score = 1.0;                      // Initialize the score

      /* Return the multiplication of scores of all query arguments.
       * Note that AND in Indri is different from AND in Boolean, it uses
       * docIteratorHasMatchMin, so we need to check whether docid matches.
       */
        for(int i = 0; i < this.args.size(); i++){
            QrySop q_i = (QrySop) this.args.get(i);
            score *= q_i.getDefaultScore(r, docid);
        }

        // normalized by geometric mean of query size
        score = Math.pow(score, 1.0 / this.args.size());
        return score;
    }


    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri(RetrievalModel r) throws IOException{
        double score = 1.0;                      // Initialize the score
        int docid = this.docIteratorGetMatch();  // Current document id

      /* Return the multiplication of scores of all query arguments.
       * Note that AND in Indri is different from AND in Boolean, it uses
       * docIteratorHasMatchMin, so we need to check whether docid matches.
       */
        for(int i = 0; i < this.args.size(); i++){
            QrySop q_i = (QrySop) this.args.get(i);

            if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid)
                score *= q_i.getScore(r);
            else
                score *= q_i.getDefaultScore(r, docid);
        }

        // normalized by geometric mean of query size
        score = Math.pow(score, 1.0 / this.args.size());
        return score;
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
			  QrySop q_i = (QrySop) this.args.get(i);
			  score = Math.min(score, q_i.getScore(r));
		  }
		  return score;
	  }
	  else
		  return 0.0;
  }
}
