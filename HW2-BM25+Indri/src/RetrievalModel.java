/** 
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.util.Map;

/**
 *  The root class in the retrieval model hierarchy.  This hierarchy
 *  is used to create objects that provide fast access to retrieval
 *  model parameters and indicate to the query operators how the query
 *  should be evaluated.
 */
public abstract class RetrievalModel {

  /**
   *  The name of the default query operator for the retrieval model.
   *  @return The name of the default query operator.
   */
  public abstract String defaultQrySopName ();


  /**
   * Set the parameters for the retrieval model.
   * @param param A map containing the customized parameters.
   */
  public abstract void setParameters(Map<String, String> param);

}
