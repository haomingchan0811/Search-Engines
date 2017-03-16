/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;
import java.util.Vector;

/**
 *  The WAND operator for all retrieval models.
 */
public class QrySopWAnd extends QrySop {

    // corresponding weights for arguments
    protected Vector<Double> weights;
    private double total_Weights;

    /**
     *  Compute the total weights for the WSUM operator.
     */
    public void initializeWeights(){
        this.total_Weights = 0.0;
        for(int i = 0; i < this.weights.size(); i++)
            this.total_Weights += this.weights.get(i);
    }

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch(RetrievalModel r) {
      if(r instanceof RetrievalModelIndri || r instanceof RetrievalModelIndriExpansion)
          return this.docIteratorHasMatchMin(r);
      else
          throw new IllegalArgumentException
                  (r.getClass().getName() + " doesn't support the WAND operator.");
//          return this.docIteratorHasMatchAll(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore(RetrievalModel r) throws IOException {
//    if(r instanceof RetrievalModelUnrankedBoolean)
//        return this.getScoreUnrankedBoolean(r);
//
//    else if(r instanceof RetrievalModelRankedBoolean)
//        return this.getScoreRankedBoolean(r);

      if(r instanceof RetrievalModelIndri || r instanceof RetrievalModelIndriExpansion)
          return this.getScoreIndri(r);
      else
          throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the WAND operator.");
  }

    /**
     *  Get a default score for a document if docIteratorHasMatch doesn't matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @param docid The document id to compute the default score
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException{
        initializeWeights();                     // BUG!!: forget to initialize weights
        double score = 1.0;                      // Initialize the score

      /* Return the multiplication of scores of all query arguments.
       * Note that AND in Indri is different from AND in Boolean, it uses
       * docIteratorHasMatchMin, so we need to check whether docid matches.
       */
        for(int i = 0; i < this.args.size(); i++){
            QrySop q_i = (QrySop) this.args.get(i);

            // exponent of current term under the WAND operator
            double exponent = this.weights.get(i) / this.total_Weights;

            score *= Math.pow(q_i.getDefaultScore(r, docid), exponent);
        }
        return score;
    }


    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri(RetrievalModel r) throws IOException{
        initializeWeights();
        double score = 1.0;                      // Initialize the score
        int docid = this.docIteratorGetMatch();  // Current document id

      /* Return the multiplication of scores of all query arguments.
       * Note that AND in Indri is different from AND in Boolean, it uses
       * docIteratorHasMatchMin, so we need to check whether docid matches.
       */
        for(int i = 0; i < this.args.size(); i++){
            QrySop q_i = (QrySop) this.args.get(i);

            // exponent of current term under the WAND operator
            double exponent = this.weights.get(i) / this.total_Weights;

            if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid)
                score *= Math.pow(q_i.getScore(r), exponent);
            else
                score *= Math.pow(q_i.getDefaultScore(r, docid), exponent);
        }
        return score;
    }


//    /**
//   *  getScore for the UnrankedBoolean retrieval model.
//   *  @param r The retrieval model that determines how scores are calculated.
//   *  @return The document score.
//   *  @throws IOException Error accessing the Lucene index
//   */
//  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
//      if(!this.docIteratorHasMatchCache())
//          return 0.0;
//      else
//          return 1.0;
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
