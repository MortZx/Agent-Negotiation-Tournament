package group30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import negotiator.Bid;
import negotiator.BidHistory;
import negotiator.bidding.BidDetails;
import negotiator.issue.Issue;
import negotiator.issue.Value;
import negotiator.issue.ValueDiscrete;
import negotiator.utility.EvaluatorDiscrete;

/**
 * 1 entry per opponent
 */
public class OpponentModel {

    // Bidding history of opponent
    private BidHistory bidHistory;

    // total number of issues in the domain
    private int totalIssues;

    // IDs of the issues
    private int[] issueIds;

    // Values(preferences) of the issues
    private EvaluatorDiscrete[] issues;

    // List for historical utility values
    List<Double> uHistorical;


    /*FIELD GETTERS */

    public BidHistory getBidHistory() {
        return this.bidHistory;
    }

    public int getTotalIssues() {
        return this.totalIssues;
    }

    public int[] getIssueIds() {
        return this.issueIds;
    }

    public EvaluatorDiscrete[] getIssues() {
        return this.issues;
    }

    /**
     * Constructor function
     * @param exampleBid An example bid used to initialise issue space
     */
    public OpponentModel(Bid exampleBid) {

        this.bidHistory = new BidHistory();

        List<Issue> issues = exampleBid.getIssues();
        this.totalIssues = issues.size();
        this.issueIds = new int[getTotalIssues()];
        this.uHistorical = new ArrayList<>();

        // Map issue IDs
        for (int i = 0; i < getTotalIssues(); i++) {
            int issueID = exampleBid.getIssues().get(i).getNumber();
            getIssueIds()[i] = issueID;
        }

        // Create evaluators for each issue
        this.issues = new EvaluatorDiscrete[getTotalIssues()];
        for (int i = 0; i < getTotalIssues(); i++) {
            getIssues()[i] = new EvaluatorDiscrete();
        }
    }

    /**
     * Add bid to the opponent's bidding history
     * @param bid bid to add to history
     */
    public void addBid(Bid bid) {
        getBidHistory().add(new BidDetails(bid, 0));
        this.setWeights();
    }

    /**
     * Set weights  & values of each issue
     */
    public void setWeights() {

        HashMap<ValueDiscrete, Double> values = this.setWeightsIssueValues();

        //setting weights based on frequency ??
        int[] changesIssues = getChangesIssues();
        double[] weights = new double[getTotalIssues()];
        double totalWeight = 0.0;
        int rounds = getBidHistory().size();

        // Iterate over all the issues
        for (int i = 0; i < getTotalIssues(); i++) {

            //iterate over the options to extract the frequency
            for (ValueDiscrete value : values.keySet()) {
                try {
                    double frequency = values.get(value).doubleValue();

                    weights[i] +=  Math.pow(frequency,2) / Math.pow(rounds, 2);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Keep the total weight to normalize
            totalWeight += weights[i];
        }

        // Normalize the weights of the issues
        for (int i = 0; i < getTotalIssues(); i++) {
            double normalizedWeight = weights[i] / totalWeight;
            getIssues()[i].setWeight(normalizedWeight);
        }

    }

    /**
     * Set weights for the values of each issue using frequency analysis
     */
    private HashMap<ValueDiscrete, Double> setWeightsIssueValues() {
        HashMap<ValueDiscrete, Double> values = new HashMap<ValueDiscrete, Double>();

        // Iterate through issues
        for (int i = 0; i < getTotalIssues(); i++) {
            // keys of HashMap are the discrete values of the issues (options)
            // values of HashMap is the frequency (n# of times) of the options

            // Iterate through bidding history
            // calculate issue frequency
            for (int j = 0; j < getBidHistory().size(); j++) {
                ValueDiscrete value = (ValueDiscrete) (getBidHistory().getHistory().get(j).getBid()
                        .getValue(getIssueIds()[i]));
                if (values.containsKey(value)) {
                    values.put(value, values.get(value) + 1);
                } else {
                    values.put(value, 1.0);
                }
            }

            // Get max n# of times a value(option) was used
            double max = 0.0;
            for (ValueDiscrete value : values.keySet()) {
                if (values.get(value) > max)
                    max = values.get(value);
            }

            // evaluation values of each issue = n# times each value was used divided by max n# times value was used
            // (max is 1)
            for (ValueDiscrete value : values.keySet()) {
                try {
                    int rounds = getBidHistory().size();
                    //populating the evaluation of each discrete issue for every issue to calculate utility
                    getIssues()[i].setEvaluationDouble(value, values.get(value) / max);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        //return the hashmap because the frequency needs to be used in the weight calculation
        return values;
    }

    /**
     * @return an array of the frequency of change of each issue
     */
    private int[] getChangesIssues()
    {
        return getChangesIssues(getBidHistory().size());
    }

    /**
     * @return an array of the frequency of change of each issue for the last x rounds
     */
    private int[] getChangesIssues(int rounds) {
        // store the frequency (n# of times) each issue has changed
        int[] frequencyChange = new int[getTotalIssues()];

        // Iterate through issues
        for (int i = 0; i < getTotalIssues(); i++) {
            Value prevRoundValue = null;
            Value currentValue;
            int count = 0;

            // Iterate over the last x rounds bids from the bidding history
            // backwards iteration to simplify first iteration
            for (int j=getBidHistory().size()-1; j>getBidHistory().size()-rounds-1; j--){
                currentValue = getBidHistory().getHistory().get(j).getBid().getValue(getIssueIds()[i]);

                // If it's not the first value and the current value is different from the previous one,
                // value has changed so increase frequency count
                if (prevRoundValue != null && !prevRoundValue.equals(currentValue))  count++;

                prevRoundValue = currentValue;
            }
            frequencyChange[i] = count;
        }
        return frequencyChange;
    }

    /**
     * @param rounds only consider the last x rounds
     * @return in range 0-1, how hardHeaded an agent is
     * 1 means no change in bids in previous rounds --> hardHeaded
     * 0 means all bids changed in previous rounds --> conceder
     */
    public Double hardHeaded(int rounds)
    {
        // bidHistory size must be at least equal to round n#
        if (getBidHistory().size() < rounds) return null;

        int[] frequencyChange = this.getChangesIssues(rounds);
        int sum = 0;
        for (int count: frequencyChange){
            sum += count;
        }
        double hardHead = 1 - (sum/(double)getTotalIssues())/(double)rounds;
        //System.out.println("Opponent hardheadedness is :" + hardHead);
        return hardHead;
    }

    /**
     * Calculate estimated utility of opponent for a bid
     * @param bid Bid to estimate opponent utility from
     * @return estimated utility for that bid
     */
    public double getOpponentUtility(Bid bid) {
        double utility = 0.0;

        //Extract bid values hashMap
        HashMap<Integer, Value> bidValues = bid.getValues();

        // Iterate over the issues, for each issue extract the opponent's pick
        // we extract the pick so we can calculate the total utility of the
        // opponent's bid
        for (int i = 0; i < getTotalIssues(); i++) {
            // Get weight of current issue
            double weight = getIssues()[i].getWeight();

            //ValueDiscrete -e.g. chips and nuts
            ValueDiscrete value = (ValueDiscrete) bidValues.get(getIssueIds()[i]);

            //utility is the preference for a discrete issue
            if (( getIssues()[i]).getValues().contains(value)) {
                //converting Double class (object - abstract type) to a double built-in type (see example below)
                //getDoubleValue(ValueDiscrete value) method returns a Double object
                // u(i1, i2, i3, i4) = w1 * u(i1) + w2 * u(i2 ) + w3 · u(i3) + w4 * u(i4)
                utility += getIssues()[i].getDoubleValue(value).doubleValue() * weight;
            }
        }
        //this is the utility of the entire bid
        return utility;
    }

    /**
     * Add our utility based on opponent's bidding history
     * @param lastReceivedBidUtility
     */
    public void adduHistorical(double lastReceivedBidUtility) {
        this.uHistorical.add(lastReceivedBidUtility);
    }
}