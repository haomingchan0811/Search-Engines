/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 *  The query expansion process for Indri retrieval model.
 */
public class QryExpansion {

  public ScoreList getScoreList(int qid, Qry q, RetrievalModelIndri model) throws IOException{

//      int trecOutputLength = 100;
      ScoreList r = new ScoreList();

      if(!model.getFilePath("fbInitialRankingFile").equals(""))
          r = model.getInitialRanking(qid);
      else{
          q.initialize(model);

          while (q.docIteratorHasMatch(model)) {
              int docid = q.docIteratorGetMatch();
              double score = ((QrySop) q).getScore(model);
              r.add(docid, score);
              q.docIteratorAdvancePast(docid);
          }
      }
      r.sort();

      /* extract all candidate terms */
      double fbDocs = model.getParam("fbDocs");
      double fbTerms = model.getParam("fbTerms");

      Set<String> candidates = new HashSet<>();
      for(int i = 0; i < fbDocs; i++){
          for(int j = 0; j < r.size(); j++){
              TermVector vec = new TermVector(r.getDocid(j), "body");
              int numTerms = vec.stemsLength();
              for(int k = 1; k < numTerms; k++){
                  String term = vec.stemString(k);
//                  System.out.println(term);
                  if(!term.contains("."))
                      candidates.add(term);
              }
          }
      }

      // Compute score for each candidate term
      HashMap<String, Double> map = new HashMap<>();
      for(String term: candidates)
          map.put(term, computeScore(term));



      // expand the query
      List OrderedList = sortByValue(map);

      for(int i = 0; i < OrderedList.size() && i < fbTerms; i++) {
          String obj = String.valueOf(OrderedList.get(i));
          String [] term = obj.split("=");
          System.out.println(String.format("%s, %s", term[0], term[1]));
      }

      // write the expanded query to a file


      return r;
  }

  public double computeScore(String term){

      return 0.0;
  }

    /**
     *  Sort a HashMap by its value.
     */
  public List sortByValue(HashMap<String, Double> map){
      List list = new ArrayList(map.entrySet());

      Collections.sort(list, new Comparator<Map.Entry>(){
          public int compare(Map.Entry a, Map.Entry b){
              return ((Comparable)(b).getValue()).compareTo((a).getValue());
          }
      });

      return list;
  }


}
