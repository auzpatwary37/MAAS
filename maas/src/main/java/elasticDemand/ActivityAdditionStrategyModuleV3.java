package elasticDemand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.replanning.PlanStrategyModule;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.replanning.ReplanningContext;
import com.google.common.collect.Sets;

import createBus.BusDataExtractor;
import optimizerAgent.MaaSUtil;

public class ActivityAdditionStrategyModuleV3 implements PlanStrategyModule{
	
	private final double marginalUtilityOfDurationSway;
	private final double marginalUtilityOfDistanceAndTime;
	private final double marginalUtilityOfAttraction;
	

	private int successfulCases= 0;
	private Map<String,Map<Id<Node>,Set<Coord>>> activityLocationMap;// This has to be modified (Maybe add a count?)
	private Map<String,Map<Id<Node>,Double>> attractionMap;
	
	private LinkedHashMap<String,Double> activityDurationMap;
	private Set<String> unmodifiableActivities;
	private Set<Plan> plans;
	private Network network;
	private Scenario scenario;
	private static final Logger logger = Logger.getLogger(ActivityAdditionStrategyModuleV3.class);
	
	private DrawFromChoice<SlotActLocationTriplet> draw = null;
	public ActivityAdditionStrategyModuleV3(Map<String,Map<Id<Node>,Double>> attractionMap,Map<String,Map<Id<Node>,Set<Coord>>> activityLocationMap, 
			Map<String,Double> activityDurationMap,Set<String>unmodifiableActivities, Network net,Scenario scenario, String choiceMethod, ActivityAdditionConfigGroup actConfig) {
		this.activityLocationMap = activityLocationMap;
		this.scenario = scenario;
		this.activityDurationMap =activityDurationMap.entrySet().stream()
                .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		this.attractionMap = attractionMap;
		this.unmodifiableActivities = unmodifiableActivities;
		this.network = net;
		
		switch(choiceMethod) {
		case DrawFromChoice.bestDraw:
			this.draw = new BestDraw<SlotActLocationTriplet>();
			break;
		case DrawFromChoice.randomDraw:
			this.draw = new RandomDraw<SlotActLocationTriplet>();
			break;
		case DrawFromChoice.logitDraw:
			this.draw = new LogitDraw<SlotActLocationTriplet>();
			break;
		default:
			logger.warn("Unknown choice draw method for activity addition choice. Swtiching to logit.");
		
		}
		this.marginalUtilityOfAttraction = actConfig.getMarginalUtilityOfAttraction();
		this.marginalUtilityOfDistanceAndTime = actConfig.getMarginalUtilityOfDistanceAndTime();
		this.marginalUtilityOfDurationSway = actConfig.getMarginalUtilityOfDurationSway();
	}
	
	@Override
	public void prepareReplanning(ReplanningContext replanningContext) {
		plans = new HashSet<>(); 
		successfulCases= 0;
	}

	@Override
	public void handlePlan(Plan plan) {
		plans.add(plan);
	}

	@Override
	public void finishReplanning() {
		this.plans.parallelStream().forEach(plan->{
			basePlanV2 p = new basePlanV2(plan, activityDurationMap, unmodifiableActivities, activityLocationMap,attractionMap, network,marginalUtilityOfDurationSway,marginalUtilityOfDistanceAndTime,marginalUtilityOfAttraction,this.scenario.getPopulation().getPersons().size(),draw,scenario);
			p.generateModifiedPlan();
			if(p.ifSuccessful)successfulCases++;
		});
		logger.info("Successfully added activities to "+successfulCases + " cases out of "+this.plans.size()+" cases.");
		this.plans.clear();
	}
	
}

class basePlanV2{
	private final double marginalUtilityOfDurationSway;
	private final double marginalUtilityOfDistanceAndTime;
	private final double marginalUtilityOfAttraction;
	public final double totalPopulation;
	private final int maxChoices = 5;
	boolean ifSuccessful = false;
	private List<Activity> activities = new ArrayList<>();
	private List<Leg> legs = new ArrayList<>();
	private Plan plan;
	private Map<String,Double> activityDurations;
	private Set<String> unModifiableActs;
	private Map<String,Map<Id<Node>,Double>> activityAttractionMap;
	private Map<String,Map<Id<Node>,Set<Coord>>> activityLocationMap;
	private Network network;
	private Network matsimNetwork;
	private Map<Integer,basicSlotInfo> slots = new HashMap<>();
	private static final Logger logger = Logger.getLogger(basePlan.class);
	private DrawFromChoice<SlotActLocationTriplet> draw;
	public basePlanV2(Plan plan, Map<String,Double>actDuration, Set<String>unModifiableActs, Map<String,Map<Id<Node>,Set<Coord>>> activityLocationMap, Map<String,Map<Id<Node>,Double>> activityAttractionMap, Network network,
			double marginalUtilityOfDurationSway,double marginalUtilityOfDistanceAndTime,double marginalUtilityOfAttraction,double totalPopulation, DrawFromChoice<SlotActLocationTriplet> draw, Scenario sceanrio) {
		this.plan = plan;
		this.draw = draw;
		this.activityDurations = actDuration;
		this.stripToBasePlan(plan);
		this.unModifiableActs = unModifiableActs;
		this.activityLocationMap = activityLocationMap;
		this.activityAttractionMap =activityAttractionMap;
		this.network = network;
		this.marginalUtilityOfDurationSway = marginalUtilityOfDurationSway;
		this.marginalUtilityOfDistanceAndTime = marginalUtilityOfDistanceAndTime;
		this.marginalUtilityOfAttraction = marginalUtilityOfAttraction;
		this.matsimNetwork = sceanrio.getNetwork();
		this.totalPopulation = totalPopulation;
	}
	
	private void stripToBasePlan(Plan plan) {
		boolean ptCont = false;
		int actCount = 0;
		double previousLegEndTime = 0;
		for(PlanElement pe:plan.getPlanElements()) {
			if(pe instanceof Activity && !((Activity)pe).getType().equals("pt interaction")) {
				Activity a = (Activity)pe;
				if(a.getStartTime().isUndefined() && previousLegEndTime!=0)a.setStartTime(previousLegEndTime);
				this.activities.add(a);
				actCount++;
				ptCont = false;
			}else if(pe instanceof Leg) {
				if(((Leg)pe).getMode().equals("transit_walk")&& ptCont == false){//pt start
					Leg leg = PopulationUtils.createLeg("pt");
					legs.add(leg);
					
					if(((Leg)pe).getDepartureTime().isDefined()) {
						this.activities.get(actCount-1).setEndTime(((Leg)pe).getDepartureTime().seconds());
						if(((Leg)pe).getTravelTime().isDefined())previousLegEndTime = ((Leg)pe).getTravelTime().seconds()+((Leg)pe).getDepartureTime().seconds();
					}
					
					ptCont = true;
				}else if(((Leg)pe).getMode().equals("car")) {
					Leg leg = (Leg)pe;
					if(((Leg)pe).getDepartureTime().isDefined()) {
						this.activities.get(actCount-1).setEndTime(((Leg)pe).getDepartureTime().seconds());
						if(((Leg)pe).getTravelTime().isDefined())previousLegEndTime = ((Leg)pe).getTravelTime().seconds()+((Leg)pe).getDepartureTime().seconds();
					}
					legs.add(leg);
					
				}	
			}
		}
		if(this.activities.size()!=this.legs.size()+1) {
			logger.debug("Debug here!!");
		}
	}
	
	public void generateActivityInsertionSlots() {//The integer will denote the no of activity after which the new activity will be inserted
		Set<String> actTypeSet = new HashSet<>();
		this.activities.stream().forEach(a->actTypeSet.add(a.getType()));
		
		for(int i = 0; i<this.activities.size()-1; i++) {
			Activity beforeAct = this.activities.get(i);
			Activity afterAct = this.activities.get(i+1);
		
			double timeFromBeforeAct = 0;
			double timeFromAfterAct = 0;
			if(!this.unModifiableActs.contains(beforeAct.getType()) && !this.unModifiableActs.contains(afterAct.getType())) {//Means it is a location good for activity insertion
				double actStartTime = 0;
				double actEndTime = 24;
				if(beforeAct.getStartTime().isDefined())actStartTime = beforeAct.getStartTime().seconds();
				if(afterAct.getEndTime().isDefined())actEndTime = afterAct.getEndTime().seconds();
				if(beforeAct.getEndTime().isUndefined()) {
					logger.debug("Activity end time of the before activity is undefined!!!");
				}
				timeFromBeforeAct = beforeAct.getEndTime().seconds()-actStartTime-this.activityDurations.get(beforeAct.getType())/2.;
				timeFromAfterAct = actEndTime-afterAct.getStartTime().seconds()-this.activityDurations.get(afterAct.getType())/2;
				
				
			}else if (!this.unModifiableActs.contains(beforeAct.getType()) && this.unModifiableActs.contains(afterAct.getType())) {
				double actStartTime = 7*3600;
				if(beforeAct.getStartTime().isDefined())actStartTime = beforeAct.getStartTime().seconds();
				timeFromBeforeAct = beforeAct.getEndTime().seconds()-actStartTime-this.activityDurations.get(beforeAct.getType())/2.;
				timeFromAfterAct = 0;
				
			}else if(this.unModifiableActs.contains(beforeAct.getType()) && !this.unModifiableActs.contains(afterAct.getType())) {
				timeFromBeforeAct = 0;
				double actEndTime = 24*3600;
				if(afterAct.getEndTime().isDefined())actEndTime = afterAct.getEndTime().seconds();
				timeFromAfterAct = actEndTime-afterAct.getStartTime().seconds()-this.activityDurations.get(afterAct.getType())/2;

			}
			if(timeFromBeforeAct<0)timeFromBeforeAct = 0;
			if(timeFromAfterAct<0)timeFromAfterAct = 0;
			if(timeFromBeforeAct>0||timeFromAfterAct>0)slots.put(i,new basicSlotInfo(i,beforeAct.getType(),afterAct.getType(),beforeAct.getCoord(),afterAct.getCoord(),timeFromBeforeAct,timeFromAfterAct));
		}	
		if(slots.isEmpty()) {
			logger.debug("No slots were generated!!! Check.");
		}
	}
	
	public Map<Id<SlotActLocationTriplet>,Double> generateActInsertionChoiceSetWithUtility(){
		Map<Id<SlotActLocationTriplet>,Double> choices = new HashMap<>();
		if(this.slots.isEmpty()) this.generateActivityInsertionSlots();
		for(basicSlotInfo slot:slots.values()) {
			
			for(Entry<String, Map<Id<Node>, Double>> e:this.activityAttractionMap.entrySet()) {
				double dur = Double.min(slot.actDuration-this.activityDurations.get(e.getKey())/2.,1800);
				if(dur>0) {
					double distance = NetworkUtils.getEuclideanDistance(slot.beforeActLocation, slot.afterActLocation)+dur*9.5;//why divide by 2 ? the logic is at most half of the available time can be allocated to the free flow travel time. Why 14? it is assumed the free flow speed.
					Map<Node,Double> nodes = new HashMap<>();
					MaaSUtil.getNearestNodesAroundTwoNodes(network, slot.beforeActLocation, slot.afterActLocation, distance).stream().forEach(n->{
						if(e.getValue().containsKey(n.getId())) {
							nodes.put(n,e.getValue().get(n.getId()));
						}
					});
					if(!e.getKey().equals(slot.beforeActType) && !e.getKey().equals(slot.afterActType) && this.activityDurations.get(e.getKey())*.5<slot.actDuration) {// Why 75%.... no idea. 
						for(Entry<Node, Double> loc:nodes.entrySet()) {
							SlotActLocationTriplet slotActLoc = new SlotActLocationTriplet(slot.slotIndex,e.getKey(),loc.getKey().getId());
							double att = loc.getValue();
							double availableTime = slot.actDuration;
							double d1 = NetworkUtils.getEuclideanDistance(slot.beforeActLocation,loc.getKey().getCoord());
							double d2 = NetworkUtils.getEuclideanDistance(slot.afterActLocation,loc.getKey().getCoord());
							double u = this.calcSlotActLocTripletUtility(e.getKey(), att, d1, d2, availableTime);
							choices.put(slotActLoc.id,u);
						}
					}
				}
			}
		}
		Map<Id<SlotActLocationTriplet>,Double> outMap =  choices.entrySet().stream()
        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
        .limit(this.maxChoices)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (e1, e2) -> e1,
                HashMap::new));
		return outMap;
	}
	
	private double calcSlotActLocTripletUtility(String type,double att, double d1, double d2, double dur) {
		double timeUtil = 0;
		if(dur-this.activityDurations.get(type)<0)timeUtil = this.marginalUtilityOfDurationSway*Math.pow((dur-this.activityDurations.get(type)),2);
		return this.marginalUtilityOfAttraction/this.totalPopulation*att+this.marginalUtilityOfDistanceAndTime*(d1+d2)+timeUtil;
	}
	
	public Plan generateModifiedPlan() {
		Map<Id<SlotActLocationTriplet>,Double> choiceSet = this.generateActInsertionChoiceSetWithUtility();
		if(choiceSet.isEmpty())return plan;
		
		SlotActLocationTriplet info = new SlotActLocationTriplet(this.draw.draw((choiceSet)));
		basicSlotInfo slot = this.slots.get(info.slot);
		this.plan.getPlanElements().clear();
		this.plan.addActivity(this.activities.get(0));
		for(int i=0;i<this.activities.size()-1;i++) {
			if(info.slot==i && info.actType!=null) {
				this.ifSuccessful = true;
				this.activities.get(i).setEndTime(this.activities.get(i).getEndTime().seconds()-slot.timeFromBeforeAct);
				this.plan.addLeg(this.legs.get(i));
				final Coord c = new RandomDraw<Coord>().draw(this.activityLocationMap.get(info.actType).get(info.location));
				
				//Coord coord = NetworkUtils.getNearestNode(this.activityLocationMap.get(actToInsert), act.getCoord()).getCoord();
				
				Activity actNew = PopulationUtils.createActivityFromCoord(info.actType, c);
				actNew.setLinkId(BusDataExtractor.getNearestLinksExactlyByMath(matsimNetwork, actNew.getCoord(), 
						100.0, Sets.newHashSet(TransportMode.car), 70/3.6, new HashSet<>()).get(0));
				double startTime = 0;
				if(this.activities.get(i).getEndTime().isDefined()) {
					startTime = this.activities.get(i).getEndTime().seconds()+1.5*(NetworkUtils.getEuclideanDistance(this.activities.get(i).getCoord(), actNew.getCoord()))/(45*1000/3600);
				}else {
					startTime = this.legs.get(i).getDepartureTime().seconds()+1.5*(NetworkUtils.getEuclideanDistance(this.activities.get(i).getCoord(), actNew.getCoord()))/(45*1000/3600);
				}
				actNew.setStartTime(startTime);
				actNew.setEndTime(actNew.getStartTime().seconds()+this.activityDurations.get(actNew.getType())/2);
				this.plan.addActivity(actNew);
				this.plan.addLeg(PopulationUtils.createLeg(this.legs.get(i)));
				this.activities.get(i+1).setStartTime(this.activities.get(i+1).getStartTime().seconds()+slot.timeFromAfterAct);
				this.plan.addActivity(this.activities.get(i+1));
			}else {
				this.plan.addLeg(this.legs.get(i));
				this.plan.addActivity(this.activities.get(i+1));
			}
		}
		
		return this.plan;
	}
	

	
	
	
}


class basicSlotInfo{
	final int slotIndex;
	final String beforeActType;
	final String afterActType;
	final Coord beforeActLocation;
	final Coord afterActLocation;
	final double actDuration;
	final double timeFromBeforeAct;
	final double timeFromAfterAct;

	public basicSlotInfo(int slotIndex, String beforeActType, String afterActType, Coord beforeActLocation, Coord afterActLocation,  double timeFromBeforeAct, double timeFromAfterAct) {
		this.slotIndex = slotIndex;
		this.beforeActType = beforeActType;
		this.afterActType = afterActType;
		this.beforeActLocation = beforeActLocation;
		this.afterActLocation = afterActLocation;
		this.timeFromAfterAct = timeFromAfterAct;
		this.timeFromBeforeAct = timeFromBeforeAct;
		this.actDuration = this.timeFromBeforeAct+this.timeFromAfterAct;
	}
	
}

class SlotActLocationTriplet{
	final int slot;
	final String actType;
	final Id<Node> location;
	final Id<SlotActLocationTriplet> id;
	
	public SlotActLocationTriplet(int slot, String actType, Id<Node> location) {
		this.slot = slot;
		this.actType = actType;
		this.location = location;
		this.id = Id.create(slot+"____"+actType+"____"+location.toString(),SlotActLocationTriplet.class) ;
	}
	
	public SlotActLocationTriplet(Id<SlotActLocationTriplet> id) {
		String part[] = id.toString().split("____");
		this.slot = Integer.parseInt(part[0]);
		this.actType = part[1];
		this.location = Id.createNodeId(part[2]);
		this.id = id;
	}
	
	public SlotActLocationTriplet(String id) {
		String part[] = id.split("____");
		this.slot = Integer.parseInt(part[0]);
		this.actType = part[1];
		this.location = Id.createNodeId(part[2]);
		this.id = Id.create(id, SlotActLocationTriplet.class);
	}
	
	@Override
	public String toString() {
		return slot+"____"+actType+"____"+location.toString();
	}
	
	
}
