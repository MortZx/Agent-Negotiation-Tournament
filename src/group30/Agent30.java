package group30;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import negotiator.AgentID;
import negotiator.Bid;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;
import negotiator.bidding.BidDetails;
import negotiator.boaframework.SortedOutcomeSpace;
import negotiator.issue.Issue;
import negotiator.issue.IssueDiscrete;
import negotiator.issue.ValueDiscrete;
import negotiator.parties.AbstractNegotiationParty;
import negotiator.parties.NegotiationInfo;
import negotiator.utility.AdditiveUtilitySpace;
import negotiator.utility.EvaluatorDiscrete;

/**
 * The negotiation party. The Negotiation Agent
 */
public class Agent30 extends AbstractNegotiationParty {
    // Agent description
    private final String description = "Group 30";

    // Latest bid received from opponents
    private Bid lastReceivedBid = null;

    // Our utility of latest received bid
    private double lastReceivedBidUtility;

    // Map of the opponents
    private HashMap<AgentID, OpponentModel> opponentMap;

    // Percentage of time in which we'll just keep offering the maximum utility bid
    private static double PERCENTAGE_T_OFFER_MAX_U = 0.2D;

    // The max amount of the best bids to save
    private int maxAmountSavedBids = 100;

    // SOS used to choose a bid close to a given utility
    private SortedOutcomeSpace SOS;
    private Random random;

    // save best bids found while searching
    private List<BidDetails> bestGeneratedBids = new ArrayList<BidDetails>();

    // number of bids generated each round to populate bestBids array
    private int numberBidsToGenerate = 100;

    int turn;


    @Override
    public void init(NegotiationInfo info) {
        // initialise class variables
        super.init(info);
        this.opponentMap = new HashMap<>();
        this.SOS = new SortedOutcomeSpace(this.utilitySpace);
        this.random = new Random();


        AdditiveUtilitySpace additiveUtilitySpace = (AdditiveUtilitySpace) utilitySpace;
        List<Issue> issues = additiveUtilitySpace.getDomain().getIssues();

        // print preferences and weights of each issue in domain
        for (Issue issue : issues) {
            int issueNumber = issue.getNumber();
            System.out.println(">> " + issue.getName() + " weight: " + additiveUtilitySpace.getWeight(issueNumber));

            // Assuming discrete issues
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            EvaluatorDiscrete evaluatorDiscrete = (EvaluatorDiscrete) additiveUtilitySpace.getEvaluator(issueNumber);

            for (ValueDiscrete valueDiscrete : issueDiscrete.getValues()) {
                System.out.println(valueDiscrete.getValue());
                System.out.println("Evaluation(getValue): " + evaluatorDiscrete.getValue(valueDiscrete));
                try {
                    System.out.println("Evaluation(getEvaluation): " + evaluatorDiscrete.getEvaluation(valueDiscrete));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

    }

    /**
     * A human-readable description for this party.
     */
    @Override
    public String getDescription() {
        return description;
    }

    /**
     * When this function is called, it is expected that the Party chooses one of the actions from the possible
     * action list and returns an instance of the chosen action.
     * @param list
     * @return chosen action.
     */
    @Override
    public Action chooseAction(List<Class<? extends Action>> list) {
        this.turn ++;
        System.out.println("round: " + turn);


        //double H = this.opponentMap.get(0).hardHeaded(10);
        //System.out.println("Opponent H value is :" + H);

        // for first 20% of time, offer max utility bid , try for 10-20-30
        if (isMaxUtilityOfferTime()) {
            Bid maxUtilityBid = null;
            try {
                maxUtilityBid = this.utilitySpace.getMaxUtilityBid();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Exception: cannot generate max utility bid");
            }
            return new Offer(getPartyId(), maxUtilityBid);
        }

        System.out.println("Last received bid had a utility of " + getUtility(this.lastReceivedBid) + " for me." );

        // Generate an acceptable bid
        Bid ourBid = createBid();

        // Check if we should accept the latest offer given the bid we're proposing
        if (isAcceptable(ourBid)) {
            return new Accept(getPartyId(), this.lastReceivedBid);
        }

        // Offer our bid
        return new Offer(getPartyId(), ourBid);
    }

    /**
     * if still in first 20% of time when offering max utility bid?
     * @return boolean offer max utility bid? YES/NO
     */
    private boolean isMaxUtilityOfferTime() {
        return getTimeLine().getTime() < this.PERCENTAGE_T_OFFER_MAX_U;
    }

    /**
     * create new bid based on our utility and estimated opponent utility from frequency model
     * @return generated bid
     */
    private Bid createBid() {
        double nashProduct;
        Bid randomBid;

        double acceptableUtility = this.getMinAcceptableUtility();
        Bid bestBid = generateAcceptableRandomBid(acceptableUtility);
        double bestNashProduct = -1;


        // Every 10 rounds, update nash product of best saved bid from opponent
        // to increase accuracy of opponent Model by taking into account new offered bid
        if (this.turn % 10 == 0){
            this.updateNashProductUtility();
        }

        // Generate random (valid) bids and see which one has a better average utility for opponent
        // reduce by 5 number of generated bids every 10 rounds
        // only after 100 rounds when bestBids list is full
        // hold when value gets down to 10.
//        if (numberBidsToGenerate > 10 && this.turn > 100){
//            if (this.turn % 100 == 0) {
//                numberBidsToGenerate = numberBidsToGenerate - 5;
//            }
//        }

        // create numberBidsToGenerate and keep best one with best nash product
        for (int i = 0; i < maxAmountSavedBids; i++) {
            // Generate a random bid with utility above minUtility
            randomBid = generateAcceptableRandomBid(acceptableUtility);

            nashProduct = this.getNashProduct(randomBid);
            // Only save best bid (highest nash product utility)
            if (nashProduct > bestNashProduct) {
                bestBid = randomBid;
                bestNashProduct = nashProduct;
            }
        }

        // if BestBids list not full, add element
        if (this.bestGeneratedBids.size() < maxAmountSavedBids){
            this.bestGeneratedBids.add(new BidDetails(bestBid, bestNashProduct));

            // if list is full, sort in order of utility
            // only ran once when last added bid makes the list full
            if (this.bestGeneratedBids.size() == this.maxAmountSavedBids){
                this.sortBestBidsByUtility();
            }
        }

        // if bestBids list is full
        else {
            // Get bid with worst utility
            double worstBidsUtility = this.bestGeneratedBids.get(this.maxAmountSavedBids -1).getMyUndiscountedUtil();

            // between new bestBid and worst bid in list, only keep best bid out of the 2
            if (bestNashProduct > worstBidsUtility){
                this.bestGeneratedBids.remove(0);
                this.bestGeneratedBids.add(new BidDetails(bestBid, bestNashProduct));
                this.sortBestBidsByUtility();
            }

            // once list is full (after 100 rounds)
            // reduce size of bestBids list by 5 every 50 turns and maintain at min of 10
            // by removing worst 5 bids
//            if (this.bestGeneratedBids.size() > numberBidsToGenerate && this.turn % 50 == 0){
//                int w;
//                for (w = 0; w <= 5; w++) {
//                    this.bestGeneratedBids.remove(0);
//                }
//            }
        }

        // when list is full, offer 1 of top 5 bids
        if (this.bestGeneratedBids.size() >= this.maxAmountSavedBids) {
            // Get index of 1 of top 5 saved bids
            // sort list with opponent utility as key
            int index = this.maxAmountSavedBids - random.nextInt(5) - 1;
            bestBid = this.bestGeneratedBids.get(index).getBid();
        }
        return bestBid;
    }

    /**
     * Calculate minimum acceptable utility
     * @return double min acceptable utility
     */
    private double getMinAcceptableUtility()
    {
        double timeLeft = 1 - getTimeLine().getTime();
        // At start of negotiation, min utility is Ka

        // log(Tdeadline - Tnow) / conceding factor + Ka ( change value for testing)
        double minUtility = Math.log10(timeLeft) / this.getConcedingFactor() + 0.9D;

        return minUtility;
    }

    /**
     * Calculate conceding factor. If one of the opponents are hard headed concede faster
     * @return double , conceding factor
     */
    private double getConcedingFactor()
    {
        Double hardHeadedness = 0.0;
        Double current = 0.0;

        // check how hardHeaded opponent is based on last 10 rounds
        for (AgentID id : this.opponentMap.keySet()){
            current = this.opponentMap.get(id).hardHeaded(10);
            if (current != null && current > hardHeadedness){
                hardHeadedness = current;
            }
        }

        // for 90% of time, high conceding factor, maybe vary the time at which this happens, between 85-90-95
        if (this.timeline.getTime() < 0.90D){
            return 13;
        }

        // Check if opponent is hardHeaded
        // If yes, concede faster
        if (hardHeadedness > 0.6){ // chane the hardheadness thershold around 0.5, 0.6, 0.7
            return 7.0;
        }
        else{
            return 10.0;
        }
    }

    /**
     * create random bid with utility > reservation value
     * by searching acceptable utility space
     * @param minAcceptableUtility self explanitory
     * @return rand bid with U > reserve value
     */
    private Bid generateAcceptableRandomBid(double minAcceptableUtility) {
        Bid bid;
        do {
            // create random double between 0-1
            double randomDouble = this.random.nextDouble();

            // Make randomDouble in range
            double utilityRange = minAcceptableUtility + randomDouble * (1.0 - minAcceptableUtility);

            // Get a bid closest to the given utility from SOS
            bid = SOS.getBidNearUtility(utilityRange).getBid();
        } while (getUtility(bid) <= minAcceptableUtility);
        return bid;
    }

    /**
     * update nash products of bestGeneratedBids.
     */
    private void updateNashProductUtility()
    {
        for (BidDetails bid: this.bestGeneratedBids){
            bid.setMyUndiscountedUtil(this.getNashProduct(bid.getBid()));
        }
    }

    // calculate nash product of bids using utility of bid * opponent utility
    private double getNashProduct(Bid bid)
    {
        double nashProduct = this.getUtility(bid);
        for (AgentID agent : this.opponentMap.keySet()) {
            nashProduct = nashProduct * this.opponentMap.get(agent).getOpponentUtility(bid);
        }
        return nashProduct;
    }

    /**
     * Sort bestGeneratedBids by utilities
     */
    private void sortBestBidsByUtility(){
        Collections.sort(this.bestGeneratedBids, new Comparator<>() {

            @Override
            public int compare(BidDetails bid1, BidDetails bid2) {
                if (bid1.getMyUndiscountedUtil() < bid2.getMyUndiscountedUtil()) return -1;
                else if (bid1.getMyUndiscountedUtil() == bid2.getMyUndiscountedUtil()) return 0;
                else return 1;
            }
        });
    }

    /**
     * Determines if latest offered bid is acceptable
     * @param proposedBid our new offer
     * @return boolean accept YES or NO?
     */
    private boolean isAcceptable(Bid proposedBid) {
        // compare utility of our bid with utility of offered bid
        boolean decision = getUtility(this.lastReceivedBid) >= getUtility(proposedBid);

        // Get min acceptable utility
        double minUtility = this.getMinAcceptableUtility();

        // accept if utility of offered bid > utility of our bid
        // accept if utility of offered bid > minUtility
        return (decision || getUtility(this.lastReceivedBid) > minUtility);
    }

    /**
     * This method is called to inform the party that another NegotiationParty chose an Action.
     * @param sender ID of opponent
     * @param action opponent action
     */
    @Override
    public void receiveMessage(AgentID sender, Action action) {
        super.receiveMessage(sender, action);

        // If sender is making an offer
        if (action instanceof Offer) {
            // Store opponent bid as latest received bid
            this.lastReceivedBid = ((Offer) action).getBid();

            // Store our utility of latest received bid
            this.lastReceivedBidUtility = getUtility(lastReceivedBid);

            // Store the bid and utility in the opponent's history
            if (opponentMap.containsKey(sender)) {
                opponentMap.get(sender).addBid(this.lastReceivedBid);
                opponentMap.get(sender).adduHistorical(this.lastReceivedBidUtility);

            } else {
                // If new opponent, create new entry in opponentMap
                try {
                    OpponentModel newOpponent = new OpponentModel(generateRandomBid());
                    newOpponent.addBid(this.lastReceivedBid);
                    opponentMap.put(sender, newOpponent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}