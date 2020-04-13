package optimizerAgent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.matsim.api.core.v01.Customizable;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.CustomizableUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.utils.objectattributes.attributable.Attributes;

import singlePlanAlgo.MAASPackage;
import singlePlanAlgo.MAASPackages;

/**
 * For now we only try the MAAS optimizer agents
 * I will create somesort of interface later after I get more insight into the whole thing
 * 
 * @author Ashraf
 *
 */
public class OptimizerAgentCreator {

	public static void createMAASOperator(MAASPackages packages,Population population, String popOutLoc) {
		for(Entry<String, Set<MAASPackage>> operator:packages.getMassPackagesPerOperator().entrySet()) {
			//create one agent per operator
			PopulationFactory popFac = population.getFactory();
			Person person = popFac.createPerson(Id.createPersonId(operator.getKey()+"MAASAgent"));
			
			Map<String,Double> variable = new HashMap<>();
			Map<String,Tuple<Double,Double>> variableLimit = new HashMap<>();
			
			for(MAASPackage m:operator.getValue()) {
				//For now only create price of package 
				variable.put(m.getId().toString()+"_Price",100.);
				variableLimit.put(m.getId().toString()+"_Price",new Tuple<>(50.,150.));
			}
			
			MAASOperator agent = new MAASOperator(person, variable, variableLimit);
			population.addPerson(agent);

			
			
			//plan.getAttributes().putAttribute("variableName", value)
			
		}
		
		PopulationWriter popWriter = new PopulationWriter(population);
		popWriter.putAttributeConverter(VariableDetails.class, VariableDetails.getAttributeConverter());
		popWriter.write(popOutLoc);
	}
}

