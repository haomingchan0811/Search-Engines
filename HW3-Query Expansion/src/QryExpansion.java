/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.IOException;

/**
 *  The query expansion process for Indri retrieval model.
 */
public class QryExpansion {

  public boolean docIteratorHasMatch(RetrievalModel r){
	  return this.docIteratorHasMatchFirst(r);
  }


  public ScoreList getScoreList(Qry query){

  }


  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore(RetrievalModel r) throws IOException {

      if (r instanceof RetrievalModelUnrankedBoolean) {
          return this.getScoreUnrankedBoolean(r);
      } else if (r instanceof RetrievalModelRankedBoolean) {
          return this.getScoreRankedBoolean(r);
      } else if (r instanceof RetrievalModelBM25) {
          return this.getScoreBM25(r);
      } else if (r instanceof RetrievalModelIndri || r instanceof RetrievalModelIndriExpansion) {
          return this.getScoreIndri(r);
      } else {
          throw new IllegalArgumentException
                  (r.getClass().getName() + " doesn't support the SCORE operator.");
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
        if(r instanceof RetrievalModelIndri){

            // Cast model type into Indri
            RetrievalModelIndri Indri = (RetrievalModelIndri) r;

            // fetch parameters of Indri
            double mu = Indri.getParam("mu");
            double lambda = Indri.getParam("lambda");

            String field = this.getArg(0).getField();
            double lenCorpus = Idx.getSumOfFieldLengths(field);
            double lenDoc = Idx.getFieldLength(field, docid);
            double ctf = this.getArg(0).getCtf();

            // MLE of Prob(term in the collection)
            double p = ctf / lenCorpus;

            double score = (1 - lambda) * mu * p / (lenDoc + mu) + lambda * p;
            return score;
        }
        else
            return getScore(r);
    }

    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri(RetrievalModel r) throws IOException{

        // Cast model type into Indri
        RetrievalModelIndri Indri = (RetrievalModelIndri) r;

        // fetch parameters of Indri
        double mu = Indri.getParam("mu");
        double lambda = Indri.getParam("lambda");

        String field = this.getArg(0).getField();
        int docid = this.getArg(0).docIteratorGetMatch();

        double lenCorpus = Idx.getSumOfFieldLengths(field);
        double lenDoc = Idx.getFieldLength(field, docid);
        double tf = this.getArg(0).docIteratorGetMatchPosting().tf;
        double ctf = this.getArg(0).getCtf();

        // MLE of Prob(term in the collection)
        double p = ctf / lenCorpus;

        // compute p here to preserve floating point precision: no use
        double score = (1 - lambda) * (tf + mu * p) / (lenDoc + mu) + lambda * p;
        return score;
    }

    /**
     *  getScore for the BM25 retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    public double getScoreBM25(RetrievalModel r) throws IOException{
        if(this.docIteratorHasMatchCache()){
            // Cast model type into BM25
            RetrievalModelBM25 bm25 = (RetrievalModelBM25)r;

            int docid = this.getArg(0).docIteratorGetMatch();
            String field = this.getArg(0).getField();

        /*
         * Compute the RSJ (idf) weight of Okapi BMxx model
         */
            double df = this.getArg(0).getDf();
            // number of documents in the corpus
            // Bug: N in IDF is different from N_field in avg_docLen
            double N = Idx.getNumDocs();
            // restrict RSJ weight to be non-negative
            double idfWeight = Math.max(0.0, Math.log((N - df + 0.5) / (df + 0.5)));

        /*
         * Compute the tf weight of Okapi BMxx model
         */
            double tf = this.getArg(0).docIteratorGetMatchPosting().tf;
            double k1 = bm25.getParam("k1");
            double b = bm25.getParam("b");
            double docLen = Idx.getFieldLength(field, docid);
            double N_field = Idx.getDocCount(field);      // number of documents in the field
            double avg_docLen = Idx.getSumOfFieldLengths(field) / N_field;
            double tfWeight = tf / (tf + k1 * (1.0 - b + b * docLen / avg_docLen));

            // Final BM25 score for this term in a specific doc
            return idfWeight * tfWeight;  // bug: userWeight is computed in QrySopSum
        }
        else return 0.0;
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
