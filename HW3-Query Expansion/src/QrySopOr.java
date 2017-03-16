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
  		else if(r instanceof RetrievalModelIndri || r instanceof RetrievalModelIndriExpansion)
  		    return this.getScoreIndri(r);
  		else{
  			throw new IllegalArgumentException
  			(r.getClass().getName() + " doesn't support the OR operator.");
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

        // Return the score of all query arguments: 1 - MUL(1-P(q_i|d)).
        for(int i = 0; i < this.args.size(); i++){
            QrySop q_i = (QrySop) this.args.get(i);
            score *= 1 - q_i.getDefaultScore(r, docid);
        }

        // score of operator OR in Indri Model
        score = 1 - score;
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

        // Return the score of all query arguments: 1 - MUL(1-P(q_i|d)).
        for(int i = 0; i < this.args.size(); i++){
            QrySop q_i = (QrySop) this.args.get(i);
            if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid)
                score *= 1 - q_i.getScore(r);
            else
                score *= 1 - q_i.getDefaultScore(r, docid);
        }

        // score of operator OR in Indri Model
        score = 1 - score;
        return score;
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

      if (this.docIteratorHasMatchCache()) {

          double score = 0.0;

          // Current matched document id
          int docid = this.docIteratorGetMatch();
		  
		  /* Return the maximum score of all query arguments 
		   * that matches the current docid.
		   */
          for (int i = 0; i < this.args.size(); i++) {
              QrySop q_i = (QrySop) this.args.get(i);

              // BUG: forget to check whether it has a match or whether docid matches
              if (q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch() == docid)
                  score = Math.max(score, q_i.getScore(r));
          }
          return score;
      } else
          return 0.0;
  }
}
