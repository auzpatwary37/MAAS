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

	public static Activity createMaaSOperator(MaaSPackages packages,Population population, String popOutLoc, Tuple<Double,Double> boundsMultiplier) {
		
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
				variable.put(m.getId()+MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript,m.getPackageCost());
				variableLimit.put(m.getId()+MaaSUtil.MaaSOperatorPacakgePriceVariableSubscript,new Tuple<>(boundsMultiplier.getFirst()*m.getPackageCost(),boundsMultiplier.getSecond()*m.getPackageCost()));
			}
			
			
			MaaSOperator agent = new MaaSOperator(person, variable, variableLimit,act);
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
}
