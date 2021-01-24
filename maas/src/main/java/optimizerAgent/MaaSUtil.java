package optimizerAgent;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Random;
import java.util.*;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.attributable.AttributesUtils;

import maasPackagesV2.MaaSPackage;
import maasPackagesV2.MaaSPackages;
import transitCalculatorsWithFare.FareLink;

import com.google.common.collect.BiMap;

public final class MaaSUtil {
	public static final String MaaSPackagesAttributeName = "MAASPackages"; 
	public static final String CurrentSelectedMaaSPackageAttributeName = "SelectedMaaSPlan";
	public static final String MaaSDiscountReimbursementTransactionName = "MaaSdiscount";
	public static final String MaaSOperatorSubscript = "_MaaSOperator";
	public static final String MaaSOperatorFareRevenueTransactionName = "fareRevenue";
	public static final String MaaSOperatorpacakgeRevenueTransactionName = "packageRevenue";
	public static final String AgentpayForMaaSPackageTransactionName = "MaaSCost";
	public static final String MaaSOperatorAgentSubPopulationName = MaaSOperator.type;
	public static final String MaaSOperatorPacakgePriceVariableSubscript = "_price";//maas package id + MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript
	public static final String MaaSOperatorFareLinkDiscountVariableSubscript = "_discount";//fareLink.toString() + MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript
	public static final String MaaSOperatorTransitLinesDiscountVariableName = "__tlDiscount";//TransitLine.toString() + MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript
	public static final String MaaSOperatorFareLinkClusterDiscountVariableName = "__flClusterDiscount";
	public static final String operatorRevenueName = "revenue";
	public static final String dummyActivityTypeForMaasOperator = "maasOperatorAct";
	public static final String nullMaaSPacakgeKeyName = "noMass";
	public static final String PackageSoldKeyName = "packageSold";
	public static final String PackageTripKeyName = "packageTrip";
	public static final String SelfPackageTripKeyName = "selfPackageTrip";
	public static final String operatorTripKeyName = "totalTrip";
	public static final String fareSavedAttrName = "fareSaved";
	public static final String irreleventPlanFlag = "irrelevantPlan";
	public static final String uniqueMaaSIncludedPlanAttributeName = "UniquePlans";
	public static final String detourRatio = "dr";
	public static final String platformReimbursementFactorName = "alpha";
	public static final String govSubsidyName = "Subsidy";
	public static final String fareLinkOperatorReimbursementTransactionName = "fareLinkOperatorTransaction";
	public static final String maasOperatorToFareLinkOperatorReimbursementTransactionName = "maasLooseFlgain";
	public static final String projectedNullMaaS = "projectedNullMaaS";
	

	public static Activity createMaaSOperator(MaaSPackages packages, Population population, String popOutLoc, 
			Tuple<Double,Double> boundsMultiplier, Map<String,Map<String,Double>> additionalVariables, Map<String,Map<String, Tuple<Double,Double>>> additionalVariableLimits, boolean addCostVar) {
		int totalPop = population.getPersons().values().size();
		int rnd = new Random().nextInt(totalPop);
		PlanElement pe = ((Person)population.getPersons().values().toArray()[rnd]).getPlans().get(0).getPlanElements().get(0);
		Coord coord = ((Activity) pe).getCoord();
		Activity act = PopulationUtils.createActivityFromCoord(dummyActivityTypeForMaasOperator,coord);
		
		for(Entry<String, Set<MaaSPackage>> operator:packages.getMassPackagesPerOperator().entrySet()) {
			//create one agent per operator
			PopulationFactory popFac = population.getFactory();
			Person person = popFac.createPerson(Id.createPersonId(operator.getKey()));

			Map<String,Double> variable = new HashMap<>();
			Map<String,Tuple<Double,Double>> variableLimit = new HashMap<>();
			
			if(addCostVar) {
				for(MaaSPackage m:operator.getValue()) {
					//For now only create price of package 
					variable.put(MaaSUtil.generateMaaSPackageCostKey(m.getId()),m.getPackageCost());
					variableLimit.put(MaaSUtil.generateMaaSPackageCostKey(m.getId()),new Tuple<>(boundsMultiplier.getFirst()*m.getPackageCost(),boundsMultiplier.getSecond()*m.getPackageCost()));
				}
			}
			if(additionalVariables!=null) {
				variable.putAll(additionalVariables.get(operator.getKey()));
				variableLimit.putAll( additionalVariableLimits.get(operator.getKey()));
			}
			MaaSOperator agent = new MaaSOperator(person, variable, variableLimit,act);
			if(population.getPersons().containsKey(agent.getId())) {
				population.getPersons().remove(agent.getId());
				
			}
			population.addPerson(agent);	
			
			//plan.getAttributes().putAttribute("variableName", value)
			
		}
		if(popOutLoc!=null) {
			PopulationWriter popWriter = new PopulationWriter(population);
			popWriter.putAttributeConverter(VariableDetails.class, VariableDetails.getAttributeConverter());
			popWriter.write(popOutLoc);
		}
		return act;
	}
	
	//Generate a unique key for the package cost variable 
	public static String generateMaaSPackageCostKey(String packageId) {
		return packageId+MaaSOperatorPacakgePriceVariableSubscript;
	}
	
	public static String retrievePackageId(String variableDetailsKey) {
		String packageId = null;
		if(variableDetailsKey.contains(MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript)) {
			packageId = variableDetailsKey.replace(MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript, "");
		}else if(variableDetailsKey.contains(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript)){
			packageId = variableDetailsKey.split("\\^")[0];
		}else if(variableDetailsKey.contains(MaaSUtil.MaaSOperatorTransitLinesDiscountVariableName)) {
			packageId = variableDetailsKey.split("\\^")[0];
		}else if(variableDetailsKey.contains(MaaSUtil.MaaSOperatorFareLinkClusterDiscountVariableName)) {
			packageId = variableDetailsKey.split("\\^")[0];
		}
		return packageId;
	}
	
	public static Set<Id<TransitLine>> retrieveTransitLineId(String varDetailsKey){
		String a = varDetailsKey.replace(MaaSUtil.MaaSOperatorTransitLinesDiscountVariableName, "");
		Set<Id<TransitLine>> tlLines = new HashSet<>();
		//String[] part =  a.split("\\^");
		for(String tl:a.split("\\^")[1].split(","))tlLines.add(Id.create(tl, TransitLine.class));
		return tlLines;
	}
	
	public static Set<FareLink> retrieveFareLinksIds(String varDetailsKey){
		String a = varDetailsKey.replace(MaaSUtil.MaaSOperatorTransitLinesDiscountVariableName, "");
		Set<FareLink> fareLinks = new HashSet<>();
		for(String fl:a.split("\\^")[1].split(","))fareLinks.add(new FareLink(fl));
		return fareLinks;
	}
	
	public static String retrieveFareLink(String variableDetailsKey) {
		if(!variableDetailsKey.contains(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript)) throw new IllegalArgumentException("This is not a fare link variable details!!!");
		variableDetailsKey.replace(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript, "");
		return variableDetailsKey.split("\\^")[1];
	}
	
	public static String retrieveName(String variableDetailsKey) {
		if(variableDetailsKey.split("\\^").length<3)return variableDetailsKey;
		return variableDetailsKey.split("\\^")[2].split("_")[0];
	}
	
	public static String generateMaaSFareLinkDiscountKey(String packageId, FareLink fareLink) {
		return packageId+"^"+fareLink.toString()+MaaSOperatorPacakgePriceVariableSubscript;
	}
	
	public static String generateMaaSFareLinkClusterDiscountKey(String packageId, Set<FareLink> fareLinks,String name) {
		String s = "";
		String fls = "";
		for(FareLink fl: fareLinks) {
			fls+=s+fl.toString();
			s=",";
		}
		return packageId+"^"+fls+"^"+name+MaaSUtil.MaaSOperatorFareLinkClusterDiscountVariableName;
	}
	
	public static String generateMaaSTransitLinesDiscountKey(String packageId, Set<Id<TransitLine>> transitLines,String name) {
		String tlString = "";
		String seperator = "";
		for(Id<TransitLine> tl:transitLines) {
			tlString+=seperator+tl.toString();
			seperator=",";
		}
		return packageId+"^"+tlString+"^"+name+MaaSUtil.MaaSOperatorTransitLinesDiscountVariableName;
	}
	
	

	
	
	public static boolean ifFareLinkVariableDetails(String key) {
		return key.contains(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript);
	}
	
	public static boolean ifMaaSPackageCostVariableDetails(String key) {
		return key.contains(MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript);
	}
	
	public static boolean ifMaaSTransitLinesDiscountVariableDetails(String key) {
		return key.contains(MaaSUtil.MaaSOperatorTransitLinesDiscountVariableName);
	}
	
	public static boolean ifMaaSFareLinkClusterVariableDetails(String key) {
		return key.contains(MaaSUtil.MaaSOperatorFareLinkClusterDiscountVariableName);
	}
	
	public static void updateMaaSVariables(MaaSPackages packages, Map<String,Double> variables1, TransitSchedule ts, BiMap<String,String> varKeys) {
		Map<String,Double> variables = new HashMap<>(variables1);
//		if(varKeys !=null&&!varKeys.isEmpty()) {
//			variables = variables1.keySet().stream().collect(Collectors.toMap(k->varKeys.inverse().get(k), k->variables1.get(k)));
//		}
		for(Entry<String, Double> var:variables.entrySet()) {
			if(MaaSUtil.ifFareLinkVariableDetails(var.getKey())) {//variable is fareLink variable.
				String pacakge = MaaSUtil.retrievePackageId(var.getKey());
				String fareLink = MaaSUtil.retrieveFareLink(var.getKey());
				if(var.getValue()>packages.getMassPackages().get(pacakge).getFullFare().get(fareLink)) {
					System.out.println("discount is higher than fare.");
				}
				packages.getMassPackages().get(pacakge).setDiscountForFareLink(new FareLink(fareLink), var.getValue());
				
			}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(var.getKey())) {//variable is package cost variable
				String pacakge = MaaSUtil.retrievePackageId(var.getKey());
				packages.getMassPackages().get(pacakge).setPackageCost(var.getValue());
			}else if(MaaSUtil.ifMaaSTransitLinesDiscountVariableDetails(var.getKey())) {
				if(var.getValue()>1) {
					System.out.println("discount ratio cannot be greater than 1!!!");
				}
				String pacakge = MaaSUtil.retrievePackageId(var.getKey());
				Set<Id<TransitLine>> linesId = MaaSUtil.retrieveTransitLineId(var.getKey());
				MaaSPackage pac = packages.getMassPackages().get(pacakge);
				Set<String> affectedFareLinks = new HashSet<>();
//				for(FareLink fl:pac.getFareLinks().values()) {
//					if(linesId.contains(fl.getTransitLine())) {
//						double fullFare = pac.getFullFare().get(fl.toString());
//						pac.setDiscountForFareLink(fl, fullFare*var.getValue());
//					}
//				}
				

				affectedFareLinks = MaaSUtil.getTransitLinesToFareLinkIncidence(linesId, ts, pacakge, packages);

				for(String fl: affectedFareLinks) {
//					if(linesId.contains(fl.getTransitLine())) {
						double fullFare = pac.getFullFare().get(fl.toString());
						pac.setDiscountForFareLink(new FareLink(fl), fullFare*var.getValue());
					//}
				}
			}else if(MaaSUtil.ifMaaSFareLinkClusterVariableDetails(var.getKey())) {
				String pacakge = MaaSUtil.retrievePackageId(var.getKey());
				Set<FareLink> affectedFareLinks = MaaSUtil.retrieveFareLinksIds(var.getKey());
				MaaSPackage pac = packages.getMassPackages().get(pacakge);
				for(FareLink fl: affectedFareLinks) {
					double fullFare = pac.getFullFare().get(fl.toString());
					pac.setDiscountForFareLink(fl, fullFare*var.getValue());
				}
			}else{//variable is not related to maas
				continue;
			}
		}
	}
	
	
	private static boolean actEquals(Activity act1,Activity act2) {
		Coord coord1 = act1.getCoord();
		Coord coord2 = act2.getCoord();
		if(!(coord1.getX()==coord2.getX() && coord1.getY()==coord2.getY())) {
			return false;
		}
		if(!act1.getType().equals(act2.getType())) return false;
		return true;
	}
	
	private static boolean legEquals(Leg leg1,Leg leg2) {
		if (leg1.getRoute().getRouteDescription()==null && leg2.getRoute().getRouteDescription()==null ) {
			return true;
		}else if(leg1.getRoute().getRouteDescription()!=null && leg2.getRoute().getRouteDescription()!=null && leg1.getRoute().getRouteDescription().equals(leg2.getRoute().getRouteDescription())) {
			return true;
		}
		return false;
	}
	
	public static boolean planEquals(Plan p1, Plan p2) {
		boolean isequal = true;
		String maas1 = (String) p1.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		String maas2 = (String) p2.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		if((maas1==null && maas2!=null)||(maas1!=null && maas2==null)) {
			return false;
		}else if(maas1!=null && maas2!=null && !maas1.equals(maas2)) {
			return false;
		}
	
		for(int i=0; i<p1.getPlanElements().size();i++) {
			if(p1.getPlanElements().get(i) instanceof Activity && p2.getPlanElements().get(i) instanceof Activity && actEquals((Activity)p1.getPlanElements().get(i),(Activity)p2.getPlanElements().get(i))){
				isequal = true;
			}else if(p1.getPlanElements().get(i) instanceof Leg && p2.getPlanElements().get(i) instanceof Leg && legEquals((Leg)p1.getPlanElements().get(i),(Leg)p2.getPlanElements().get(i))) {
				isequal = true;
			}else {
				
				return false;
			}
		}
		return isequal;
	}
	
	public static List<Plan> sortPlan(List<Plan> plans) {
        // Sort the list 
        Collections.sort(plans, new Comparator<Plan>() { 
            public int compare(Plan o1,  
                               Plan o2) 
            { 
            	if((Double)o1.getAttributes().getAttribute(MaaSUtil.detourRatio) == null) calcDetaourRatio(o1);
            	if((Double)o2.getAttributes().getAttribute(MaaSUtil.detourRatio) == null) calcDetaourRatio(o2);
                return ((Double)o1.getAttributes().getAttribute(MaaSUtil.detourRatio)).compareTo((Double)o2.getAttributes().getAttribute(MaaSUtil.detourRatio)); 
            } 
        }); 
        return plans; 
	}
 
	public static void calcDetaourRatio(Plan plan) {
		double distAct = 0;
		double distLeg = 0;
		Coord lastActCoord = null;
		for(PlanElement pe:plan.getPlanElements()){
			if(pe instanceof Leg) {
				distLeg+=((Leg)pe).getRoute().getDistance();
			}else {
				if(lastActCoord !=null) {
					distAct += NetworkUtils.getEuclideanDistance(((Activity)pe).getCoord(),lastActCoord);
				}
				lastActCoord = ((Activity)pe).getCoord();
			}
		}
		plan.getAttributes().putAttribute(MaaSUtil.detourRatio, distLeg/distAct);
 	}

	public static String createPlanformReimbursementVariableName(String operatorId) {
		return platformReimbursementFactorName+"___"+operatorId;
	}
	
	@Deprecated
	public static Set<FareLink> getTransitLineToFareLinkIncidence(Id<TransitLine> linId, TransitSchedule ts, String packageId, MaaSPackages pacs){
		Set<Id<TransitStopFacility>> stops = new HashSet<>();
		Set<FareLink> fareLinks = new HashSet<>();
		ts.getTransitLines().get(linId).getRoutes().values().stream().forEach(r->{
			r.getStops().stream().forEach(trs->{
				stops.add(trs.getStopFacility().getId());
			});
		});
		for(FareLink fl:pacs.getMassPackages().get(packageId).getFareLinks().values()) {
			if(stops.contains(fl.getBoardingStopFacility()) && stops.contains(fl.getAlightingStopFacility())) {
				fareLinks.add(fl);
			}
		}
		return fareLinks;
	}
	
	public static Set<String> getTransitLinesToFareLinkIncidence(Set<Id<TransitLine>> linesId, TransitSchedule ts, String packageId, MaaSPackages pacs){
		//Set<Id<TransitStopFacility>> stops = new HashSet<>();
		Set<String> fareLinks = new HashSet<>();
//		for(Id<TransitLine>linId:linesId) {
//			ts.getTransitLines().get(linId).getRoutes().values().stream().forEach(r->{
//				r.getStops().stream().forEach(trs->{
//					stops.add(trs.getStopFacility().getId());
//				});
//			});
//		}
//		for(FareLink fl:pacs.getMassPackages().get(packageId).getFareLinks().values()) {
//			if(stops.contains(fl.getBoardingStopFacility()) && stops.contains(fl.getAlightingStopFacility())) {
//				fareLinks.add(fl);
//			}
//		}
		Map<String,Set<Id<TransitStopFacility>>> modeSpecificStops = new HashMap<>();
		for(Id<TransitLine> lineId:linesId) {
			String mode = new ArrayList<>(ts.getTransitLines().get(lineId).getRoutes().values()).get(0).getTransportMode();
			if(!modeSpecificStops.containsKey(mode)) {
				modeSpecificStops.put(mode, new HashSet<>());
			}
			ts.getTransitLines().get(lineId).getRoutes().values().forEach(r->{
				r.getStops().forEach(trs->modeSpecificStops.get(mode).add(trs.getStopFacility().getId()));
			
			//modeSpecificStops.get(mode).addAll(c)
			});
		}
		for(FareLink fl:pacs.getMassPackages().get(packageId).getFareLinks().values()) {
			if(fl.getType().equals(FareLink.InVehicleFare)) {
				if(linesId.contains(fl.getTransitLine())) {
					fareLinks.add(fl.toString());
				}
			}else {
				if(modeSpecificStops.get(fl.getMode())!=null && modeSpecificStops.get(fl.getMode()).contains(fl.getBoardingStopFacility()) && modeSpecificStops.get(fl.getMode()).contains(fl.getAlightingStopFacility())) {
					fareLinks.add(fl.toString());
				}
			}
		}
		return fareLinks;
	}
	
	public static MaaSPackages createUnifiedMaaSPackages(MaaSPackages packages, String operatorId, String maasId) {
		MaaSPackages allPack = new MaaSPackages();
		String Id = maasId;
		int maxTaxiTrip = 0;
		double cost = 20;
		double packageExpDate = 3;
		Map<String,FareLink> fareLinks = new HashMap<>();
		Map<String,Double>discounts = new HashMap<>();
		Map<String,Double>fullFare = new HashMap<>();
		Map<String,Set<String>> operatorSpecificFareLinks = new HashMap<>();
		for(Entry<String, MaaSPackage> m : packages.getMassPackages().entrySet()) {
			fareLinks.putAll(m.getValue().getFareLinks());
			discounts.putAll(m.getValue().getDiscounts());
			fullFare.putAll(m.getValue().getFullFare());
			for(Entry<String,Set<String>>e:m.getValue().getOperatorSpecificFareLinks().entrySet()){
				if(!operatorSpecificFareLinks.containsKey(e.getKey())) {
					operatorSpecificFareLinks.put(e.getKey(), new HashSet<>());
				}
				operatorSpecificFareLinks.get(e.getKey()).addAll(e.getValue());
			}
		}
		MaaSPackage pac = new MaaSPackage(Id, operatorId, fareLinks, discounts, fullFare,operatorSpecificFareLinks, maxTaxiTrip, cost, packageExpDate);
		allPack.addMaaSPacakge(pac);
		allPack.updateOperatorToFareLinkMap();
		return allPack;
	}
	
}
