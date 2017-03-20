/**
 *  Copyright(c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 *  The query expansion process for Indri retrieval model.
 */
public class QryExpansion {

    private double fbMu, lenCorpus, fbDocs, fbTerms;
    private String field, outputPath;
    private int qid;
    private RetrievalModelIndri model;
    private ScoreList r;
    private HashMap<Integer, String> queries = new HashMap<>();


    // A utility class to create a <term, score> object.
    private class Entry {
        private String key;
        private double value;

        public Entry(String key, double value) {
            this.key = key;
            this.value = value;
        }

        public String getKey(){
            return this.key;
        }

        public double getValue(){
            return this.value;
        }
    }

    // customized comparator for priority queue: sort pair with ascending value
    class EntryComparator implements Comparator<Entry> {

        @Override
        public int compare(Entry a, Entry b) {
            return Double.compare(a.getValue(), b.getValue());
        }
    }


    public void initialize(RetrievalModelIndri model, int qid) throws IOException{
        this.model = model;
        this.field = "body";
        this.qid = qid;
        this.fbMu = model.getParam("fbMu");
        this.lenCorpus = Idx.getSumOfFieldLengths(this.field);
        this.fbTerms = model.getParam("fbTerms");
        this.fbDocs= model.getParam("fbDocs");
        this.outputPath = model.getFilePath("fbExpansionQueryFile");

//        // select the query from previous generated files to speed up for Experiment 3/4
//        String topTerms = "58: #wand( 0.034848 i 0.035076 jacquet 0.035102 stuff 0.035443 l'empereur 0.035882 page 0.036842 jgoode 0.037041 t 0.039963 plush 0.041321 review 0.041918 shirt 0.046284 video 0.047253 p 0.048001 antarctica 0.057127 6 0.065200 reader 0.065516 march 0.065986 emperor 0.068334 pittsburgh 0.069881 fact 0.070628 gift 0.072781 glitche 0.076216 animal 0.078230 mission 0.090647 cheat 0.098203 png 0.109234 picture 0.112024 kb 0.128389 rockhopper 0.849698 club 3.071810 penguin)\n" +
//                "65: #wand( 0.018024 word 0.018412 k 0.019247 culture 0.019314 sino 0.019425 hanja 0.019546 vowel 0.019549 words 0.020093 vocabulary 0.020859 also 0.021474 spelling 0.023531 pronunciation 0.024240 translate 0.026330 edit 0.027784 china 0.029129 new 0.029273 hangul 0.030369 about 0.030692 i 0.031583 use 0.033500 group 0.036522 verb 0.037235 japan 0.038510 english 0.039031 dialect 0.043739 meetup 0.044055 interested 0.045721 north 0.047662 south 0.201189 language 0.437115 korea)\n" +
//                "67: #wand( 0.002721 apob 0.002734 patient 0.002888 hypercholesterolemia 0.002926 can 0.002966 particle 0.003102 from 0.003112 density 0.003128 plasma 0.003161 total 0.003166 heart 0.003179 liver 0.003256 atherosclerosi 0.003519 mg 0.003636 dl 0.003895 lower 0.004002 increase 0.004179 low 0.004352 fat 0.005406 high 0.005545 blood 0.006311 lipid 0.006332 risk 0.006538 disease 0.007752 vldl 0.007840 triglyceride 0.008999 hdl 0.011687 lipoprotein 0.015937 ldl 0.023691 level 0.033633 cholesterol)\n" +
//                "68: #wand( 0.031321 material 0.031734 miroiterie 0.031959 gift 0.032208 conduit 0.033961 leather 0.037912 3 0.038488 trade 0.040652 co 0.041455 boi 0.041705 putt 0.041770 foam 0.043266 panel 0.045813 bag 0.047888 rigid 0.051025 2 0.051371 china 0.051732 ltd 0.053951 alu 0.054112 soft 0.055227 fitting 0.055733 board 0.062833 plastic 0.066422 product 0.075942 pipes 0.077899 cable 0.079590 1 0.111994 sheet 0.119760 door 0.158304 pipe 1.482189 pvc)\n" +
//                "73: #wand( 0.035542 girl 0.036210 mica 0.036640 de 0.036785 armstrong 0.036981 gold 0.037411 out 0.039020 let 0.043382 down 0.043540 me 0.047449 your 0.047537 heart 0.047966 don't 0.048006 5 0.049586 diamond 0.054631 song 0.059400 i 0.062519 hey 0.069831 old 0.071239 man 0.076602 e 0.079490 blues 0.087453 my 0.092396 guitar 0.093854 you 0.121947 lyrics 0.143812 tab 0.177300 love 0.337203 chord 2.640024 young 3.518500 neil)\n" +
//                "75: #wand( 0.033579 2 0.033819 funnel 0.033865 severe 0.034353 confirmed 0.034785 cloud 0.034933 april 0.035031 warning 0.036391 violent 0.039532 county 0.039676 filter 0.040760 torn 0.040879 oklahoma 0.044739 shelter 0.046459 2007 0.046629 kansas 0.047089 0 0.048245 large 0.049534 toronto 0.051531 mesh 0.052692 video 0.054850 weather 0.059644 fatality 0.060343 tori 0.064002 may 0.070969 storm 0.071206 texas 0.085360 outbreak 0.088817 damage 0.169629 toro 2.298917 tornado)\n" +
//                "77: #wand( 0.033528 price 0.033754 new 0.035201 love 0.035316 2008 0.035754 mower 0.036278 photo 0.038237 i 0.044447 dealer 0.046082 manual 0.047342 hydrostatic 0.048541 backhoe 0.049257 breakdown 0.049368 sale 0.050241 attachment 0.052718 parts 0.055453 excavator 0.057207 bobby 0.058093 skidsteer 0.059082 boxscore 0.067227 arena 0.067634 play 0.071958 ticket 0.075497 equipment 0.077542 rental 0.085483 load 0.131416 charlotte 0.137969 use 0.252432 steer 0.306159 skid 2.866750 bobcat)\n" +
//                "86: #wand( 0.013007 park 0.013080 8 0.013203 center 0.013241 fruitvale 0.013439 7 0.013541 el 0.013654 parking 0.015217 photo 0.015908 mission 0.015940 airport 0.017336 bike 0.017548 concord 0.019166 hayward 0.019169 transit 0.019332 leandro 0.019544 city 0.020346 francisco 0.021095 bay 0.021576 cerrito 0.024754 berkeley 0.036452 san 0.037037 st 0.041610 station 0.060986 am 0.064826 oakland 0.068289 30 0.074631 00 0.077140 bart 0.080964 pm 0.086786 sf)\n" +
//                "117: #wand( 0.013436 require 0.013685 ltd 0.013778 product 0.013811 test 0.013850 company 0.014202 advice 0.014678 procedure 0.014775 floor 0.016083 welfare 0.016434 tile 0.016493 survey 0.016517 acoustic 0.016888 checklist 0.016938 ceiling 0.017030 yorkshire 0.017833 operative 0.018733 analytic 0.018831 accredit 0.019186 lincolnshire 0.020686 humberside 0.021195 lung 0.022042 hse 0.022167 micron 0.022367 amosite 0.022567 scunthorpe 0.024045 uka 0.033883 danger 0.041293 mesothelioma 0.064651 removal 0.246602 asbestos)\n" +
//                "138: #wand( 0.001823 patina 0.001825 oil 0.001830 metal 0.001837 mice 0.001947 brass 0.001970 service 0.002052 black 0.002101 home 0.002211 us 0.002222 finish 0.002269 rust 0.002279 use 0.002415 silver 0.002727 copper 0.002991 about 0.003008 jacksonville 0.003210 plating 0.003399 polish 0.003754 order 0.003826 contact 0.004030 shipping 0.004197 information 0.004208 blacken 0.004598 inc 0.004710 cleaner 0.005201 solution 0.006171 product 0.008245 chemical 0.009036 company 0.027401 jax)\n" +
//                "144: #wand( 0.005425 tropical 0.005901 jazz 0.005945 saxophone 0.006070 flute 0.006106 baritone 0.006116 musical 0.006169 valve 0.006475 shipping 0.006493 bb 0.006683 br 0.006980 slide 0.007468 yamaha 0.007521 free 0.007794 flugelhorn 0.008150 bach 0.008216 lesson 0.008536 alto 0.008677 clarinet 0.008688 horn 0.009531 guitar 0.012263 music 0.012636 buy 0.013462 instrument 0.014673 tenor 0.014771 price 0.014935 bass 0.015821 brass 0.019349 trumpet 0.027510 sale 0.178763 trombone)\n" +
//                "159: #wand( 0.004973 from 0.005025 ca 0.005476 area 0.005554 1 0.005757 district 0.005893 county 0.005914 united 0.006012 elementary 0.006017 states 0.006123 hotel 0.006140 sale 0.006251 1996 0.006400 vehicle 0.006429 valley 0.006971 2009 0.007438 93257 0.007596 call 0.008164 tahoe 0.008355 chevrolet 0.008362 san 0.008647 college 0.008775 city 0.008890 service 0.014408 school 0.015810 insurance 0.017332 visalia 0.017373 559 0.019870 california 0.022192 tulare 0.102027 porterville)\n" +
//                "167: #wand( 0.023892 resort 0.024708 information 0.024804 offshore 0.024974 flyover 0.025092 bridgetown 0.025110 1 0.025904 page 0.026065 non 0.026637 blog 0.026656 about 0.027075 from 0.027336 seek 0.027717 saint 0.028105 more 0.028661 104 0.030865 lucia 0.032093 0 0.032284 restaurant 0.034420 imonz69 0.045319 vacation 0.047922 st 0.048528 travel 0.048550 island 0.051558 tourism 0.052642 villa 0.053338 beach 0.057273 caribbean 0.064011 rum 0.079350 hotel 1.700149 barbados)\n" +
//                "168: #wand( 0.006685 j 0.006993 grow 0.007014 have 0.007223 subcutaneous 0.007229 malignant 0.007251 lump 0.007270 from 0.007372 lipomatosi 0.007703 usually 0.007840 most 0.008207 benign 0.008547 soft 0.009084 cause 0.009123 fatty 0.009482 treatment 0.009677 common 0.009890 disorder 0.010035 can 0.010283 cell 0.010318 may 0.010597 liposarcoma 0.010908 health 0.011649 pmid 0.012773 dercum 0.013662 abnegato 0.013907 tissue 0.016143 disease 0.018458 skin 0.026649 tumor 0.142340 lipoma)\n" +
//                "172: #wand( 0.003376 work 0.003438 university 0.003515 bachelor 0.003623 student 0.003699 aba 0.003853 associate 0.003929 association 0.004206 your 0.004212 justice 0.004615 criminal 0.004643 texas 0.005094 technician 0.005639 law 0.005970 career 0.006637 you 0.007157 job 0.007340 assistant 0.008000 online 0.009102 certificate 0.009231 course 0.009412 education 0.009815 study 0.010517 training 0.012128 legal 0.014187 program 0.014642 school 0.015499 college 0.015763 degree 0.042065 becoming 0.117681 paralegal)\n" +
//                "175: #wand( 0.000777 grow 0.000781 similar 0.000795 cache 0.000798 vine 0.000874 my 0.000947 warning 0.000961 have 0.000986 from 0.001054 insurance 0.001075 tom 0.001081 2007 0.001088 reply 0.001097 cause 0.001134 label 0.001155 page 0.001184 health 0.001221 i 0.001236 disease 0.001254 sign 0.001285 car 0.001290 treatment 0.001394 record 0.001490 asylum 0.001593 waits 0.001680 you 0.002739 symptom 0.002873 sympton 0.004096 heartattack 0.006653 attack 0.009142 heart)\n" +
//                "182: #wand( 0.028868 way 0.029988 effects 0.030384 patch 0.031681 chantix 0.032156 ways 0.032677 habit 0.033573 i 0.035249 program 0.039536 drug 0.039780 pill 0.040052 pipe 0.040975 your 0.041615 smoke 0.045356 smoker 0.049333 weight 0.049786 nicotine 0.055811 fetish 0.057210 help 0.058643 free 0.059780 how 0.060254 cigarette 0.074008 deed 0.077734 claim 0.083477 laser 0.088620 you 0.089066 hypnosis 0.121753 cessation 0.342418 stop 1.539502 quit 1.605861 smoking)\n" +
//                "184: #wand( 0.003309 timeline 0.003496 page 0.003536 1955 0.003705 politics 0.003722 during 0.003906 powerset 0.003951 service 0.004003 1968 0.004198 from 0.004201 1896 0.004370 discrimination 0.004409 1954 0.004435 history 0.004639 women 0.004871 black 0.005135 1964 0.005649 united 0.005695 freedom 0.005707 close 0.006375 states 0.007185 bowel 0.007923 law 0.008789 act 0.009516 africa 0.010470 right 0.019277 america 0.040246 war 0.071930 rights 0.130331 movement 0.147123 civil)\n" +
//                "194: #wand( 0.010310 same 0.010320 dysplasia 0.010477 cockapoo 0.010835 label 0.011831 labradoodle 0.012276 often 0.012294 trait 0.012423 name 0.013061 terrier 0.013356 some 0.013879 different 0.014286 have 0.014572 than 0.016273 two 0.016933 from 0.017871 bred 0.018215 portmanteau 0.020414 breeder 0.020951 puppy 0.022344 poodle 0.029075 crossbred 0.029283 cross 0.031363 breeding 0.043277 designer 0.044062 hybrid 0.048552 purebred 0.051790 crossbreed 0.087230 dogs 0.113323 dog 0.135066 breed)\n" +
//                "195: #wand( 0.031533 model 0.032127 w 0.032682 consumer 0.033298 generac 0.033421 shop 0.034318 stratton 0.035640 guide 0.036489 briggs 0.037567 honda 0.038510 dryer 0.042968 free 0.043726 clean 0.043994 hose 0.045464 direct 0.048123 spray 0.048474 high 0.050619 parts 0.052462 professional 0.056602 pump 0.061029 cold 0.062330 hot 0.062828 cleaner 0.068493 gas 0.099918 karcher 0.120285 electric 0.121154 water 0.128503 power 0.238015 psi 0.714325 pressure 1.209497 washer)\n";
//        String[] query = topTerms.split("\n");
//        for(String s: query){
//            String[] p = s.split(":");
//            int id = Integer.parseInt(p[0].trim());
//            String content = p[1].trim();
//            this.queries.put(id, content);
//        }
    }

  public ScoreList getScoreList(int qid, Qry q, String originalQuery, RetrievalModelIndri model) throws IOException {

      // initialize parameter
      initialize(model, qid);

      if (!model.getFilePath("fbInitialRankingFile").equals("")) {
          this.r = model.getInitialRanking(this.qid);
          //System.out.println(model.getFilePath("fbInitialRankingFile"));
      } else {
          this.r = new ScoreList();
          q.initialize(model);

          while (q.docIteratorHasMatch(model)) {
              int docid = q.docIteratorGetMatch();
              double score = ((QrySop) q).getScore(model);
              this.r.add(docid, score);
              q.docIteratorAdvancePast(docid);
          }
      }
      this.r.sort();

      /* extract all candidate terms */
      Set<String> candidates = findCandidates();

      // Compute score for each candidate term
      PriorityQueue<Entry> pq = new PriorityQueue<>(new EntryComparator());

//      int i = 0, size = candidates.size();
      for (String term : candidates) {
          double termScore = computeScore(term);
          pq.add(new Entry(term, termScore));
//          System.out.println(String.format("Term %d out of %d", i++, size));
          if (pq.size() > this.fbTerms) pq.poll();
      }

      // expand the query
      String learnedQuery = "#wand(";
      while (pq.size() > 0) {
          Entry temp = pq.poll();
          learnedQuery += String.format(" %f %s", temp.getValue(), temp.getKey());
      }
      learnedQuery += ")";

//      // fetch learned query from previous generated file to speed up for experiment 3/4
//      String learnedQuery = this.queries.get(qid);

      // write the expanded query to a file
      if(!this.outputPath.equals(""))
          writeFile(learnedQuery);

      // rewrite the query by combining the expanded query with the original one
      double originWeight = model.getParam("fbOrigWeight");
      String expandedQuery = String.format("#wand(%f %s %f %s)", originWeight, originalQuery, 1 - originWeight, learnedQuery);

      // run the expanded query to retrieve documents
      return processQuery(expandedQuery);
  }

    /**
     * write the query to file along with its id.
     * @param s A string that contains a query.
     * @throws IOException Error accessing the index
     */
    public void writeFile(String s) throws IOException{
        PrintWriter writer = new PrintWriter(new FileWriter(this.outputPath, true));
        writer.println(String.format("%d: %s", this.qid, s));
        writer.close();
    }

    /**
     * retrieve all the terms in the top K documents.
     * @return A set containing all the candidate terms.
     * @throws IOException Error accessing the index
     */
    public Set<String> findCandidates() throws IOException{
        Set<String> candidates = new HashSet<>();
        for (int i = 0; i < this.fbDocs; i++) {
            TermVector vec = new TermVector(this.r.getDocid(i), "body");
            int numTerms = vec.stemsLength();

            for (int k = 1; k < numTerms; k++) {
                String term = vec.stemString(k);
                if (!term.contains("."))   // terms having "." may confuse the parser
                    candidates.add(term);
            }
        }
        return candidates;
    }

    /**
     * Process one query.
     * @param qString A string that contains a query.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    public ScoreList processQuery(String qString) throws IOException {

        String defaultOp = this.model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        if (q != null) {
            ScoreList s = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(this.model);

                while (q.docIteratorHasMatch(this.model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(this.model);
                    s.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            return s;
        }
        else return null;
    }

    /**
     * compute the term score (with idf effect) of all top K documents.
     * @param term target candidate term.
     * @return the term score (with idf effect) of all top K documents.
     * @throws IOException Error accessing the index
     */
    public double computeScore(String term) throws IOException{
        double score = 0.0;

        // a form of idf to penalize frequent terms
        double ctf = Idx.getTotalTermFreq(this.field, term);
        double p = ctf / this.lenCorpus;      // MLE of Prob(term in the collection)
        double idf = Math.log(this.lenCorpus / ctf);

        for(int i = 0; i < this.fbDocs; i++){

            // Indri score for the original query for this document
            int docid = this.r.getDocid(i);
            double docScore = this.r.getDocidScore(i);
            TermVector vec = new TermVector(docid, this.field);

            // score for the candidate term conditioned on this document
            int idx = vec.indexOfStem(term);   // index of the term in this document
            double tf = (idx == -1? 0.0: vec.stemFreq(idx));
            double docLen = Idx.getFieldLength(this.field, docid);
            docScore *= (tf + this.fbMu * p) / (docLen + this.fbMu);
            //System.out.println(String.format("score for %s in doc %d: %f", term, i, docScore));

            score += docScore;
        }
        return score * idf;
    }

//    /**
//     *  Sort a HashMap by its value.
//     */
//  public List sortByValue(HashMap<String, Double> map){
//      List list = new ArrayList(map.entrySet());
//
//      Collections.sort(list, new Comparator<Map.Entry>(){
//          public int compare(Map.Entry a, Map.Entry b){
//              return ((Comparable)(b).getValue()).compareTo((a).getValue());
//          }
//      });
//
//      return list;
//  }
    
}

