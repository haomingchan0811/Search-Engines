/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 *  The query expansion process for Indri retrieval model.
 */
public class QryExpansion {

  public ScoreList getScoreList(Qry q, RetrievalModelIndri model) throws IOException{

      int trecOutputLength = 100;
      ScoreList r = new ScoreList();

      if(!model.getFilePath("fbInitialRankingFile").equals("")){


      }
      else{
          q.initialize(model);

          while (q.docIteratorHasMatch(model)) {
              int docid = q.docIteratorGetMatch();
              double score = ((QrySop) q).getScore(model);
              r.add(docid, score);
              q.docIteratorAdvancePast(docid);
          }
          r.sort();
      }


      // expand the query


      // write the expanded query to a file




      return r;
  }



}
