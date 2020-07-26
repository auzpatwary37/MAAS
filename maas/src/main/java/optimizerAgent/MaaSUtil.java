package optimizerAgent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.Random;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.collections.Tuple;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import transitCalculatorsWithFare.FareLink;

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
	public static final String operatorRevenueName = "revenue";
	public static final String dummyActivityTypeForMaasOperator = "maasOperatorAct";
	public static final String nullMaaSPacakgeKeyName = "noMass";
	public static final String PackageSoldKeyName = "packageSold";
	public static final String PackageTripKeyName = "packageTrip";
	public static final String operatorTripKeyName = "totalTrip";

	public static Activity createMaaSOperator(MaaSPackages packages, Population population, String popOutLoc, 
			Tuple<Double,Double> boundsMultiplier) {
		int totalPop = population.getPersons().values().size();
		int rnd = new Random().nextInt(totalPop);
		PlanElement pe = ((Person)population.getPersons().values().toArray()[rnd]).getPlans().get(0).getPlanElements().get(0);
		Coord coord = ((Activity) pe).getCoord();
		Activity act = PopulationUtils.createActivityFromCoord(dummyActivityTypeForMaasOperator,coord);
		
		for(Entry<String, Set<MaaSPackage>> operator:packages.getMassPackagesPerOperator().entrySet()) {
			//create one agent per operator
			PopulationFactory popFac = population.getFactory();
			Person person = popFac.createPerson(Id.createPersonId(operator.getKey()+MaaSUtil.MaaSOperatorSubscript));

			Map<String,Double> variable = new HashMap<>();
			Map<String,Tuple<Double,Double>> variableLimit = new HashMap<>();
			
			for(MaaSPackage m:operator.getValue()) {
				//For now only create price of package 
				variable.put(MaaSUtil.generateMaaSPackageCostKey(m.getId()),m.getPackageCost());
				variableLimit.put(MaaSUtil.generateMaaSPackageCostKey(m.getId()),new Tuple<>(boundsMultiplier.getFirst()*m.getPackageCost(),boundsMultiplier.getSecond()*m.getPackageCost()));
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
			packageId = variableDetailsKey.split("^")[0];
		}
		return packageId;
	}
	
	public static String retrieveFareLink(String variableDetailsKey) {
		if(!variableDetailsKey.contains(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript)) throw new IllegalArgumentException("This is not a fare link variable details!!!");
		variableDetailsKey.replace(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript, "");
		return variableDetailsKey.split("^")[1];
	}
	
	public static String generateMaaSFareLinkDiscountKey(String packageId, FareLink fareLink) {
		return packageId+"^"+fareLink.toString()+MaaSOperatorPacakgePriceVariableSubscript;
	}
	
	public static String generateOperatorId(String operatorId) {
		return operatorId+MaaSUtil.MaaSOperatorSubscript;
	}

	public static String retrieveOperatorIdFromOperatorPersonId(Id<Person> personId) {
		if(!personId.toString().contains(MaaSUtil.MaaSOperatorSubscript)) throw new IllegalArgumentException("This is not an MaaS operator!!!");
		return personId.toString().replace(MaaSUtil.MaaSOperatorSubscript, "");
	}
	
	public static boolean ifFareLinkVariableDetails(String key) {
		return key.contains(MaaSUtil.MaaSOperatorFareLinkDiscountVariableSubscript);
	}
	
	public static boolean ifMaaSPackageCostVariableDetails(String key) {
		return key.contains(MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript);
	}
	
	public static void updateMaaSVariables(MaaSPackages packages, Map<String,Double> variables) {
		for(Entry<String, Double> var:variables.entrySet()) {
			if(MaaSUtil.ifFareLinkVariableDetails(var.getKey())) {//variable is fareLink variable.
				String pacakge = MaaSUtil.retrievePackageId(var.getKey());
				String fareLink = MaaSUtil.retrieveFareLink(var.getKey());
				packages.getMassPackages().get(pacakge).setDiscountForFareLink(new FareLink(fareLink), var.getValue());
				
			}else if(MaaSUtil.ifMaaSPackageCostVariableDetails(var.getKey())) {//variable is package cost variable
				String pacakge = MaaSUtil.retrievePackageId(var.getKey());
				packages.getMassPackages().get(pacakge).setPackageCost(var.getValue());
				
			}else{//variable is not related to maas
				continue;
			}
		}
	}
	
}
