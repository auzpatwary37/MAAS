package singlePlanAlgo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.population.PopulationUtils;

import optimizerAgent.MaaSUtil;

public class Analysis {

	public static void main(String[] args) {
		Population outputPopulation = PopulationUtils.readPopulation("toyScenarioLarge/output_optim_platform/output_plans.xml.gz");
		System.out.println("Total population = " + outputPopulation.getPersons().size());
		for(Entry<Id<Person>, ? extends Person> p:new HashMap<>(outputPopulation.getPersons()).entrySet()) {
			if(p.getValue().getSelectedPlan().getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)==null) {
				outputPopulation.removePerson(p.getKey());
			}
			
		}
		
		new PopulationWriter(outputPopulation).write("toyScenarioLarge/output_optim_platform/output_maasPlans.xml");
		double packCost = 46.78345336308337;
		Population pop = PopulationUtils.readPopulation("toyScenarioLarge/output_optim_platform/output_maasPlans.xml");
		double subOptimalPlans = 0;
		double moneySaved = 0;
		for(Person p:pop.getPersons().values()){
			if(PopulationUtils.getSubpopulation(p).equals(MaaSUtil.MaaSOperatorAgentSubPopulationName))continue;
			List<Double> scores = new ArrayList<>();
			for(Plan pl:p.getPlans()) {
				scores.add(pl.getScore());
			}
			double maxScore = Collections.max(scores);
			if(p.getSelectedPlan().getScore()<maxScore) {
				subOptimalPlans++;
			}
			Double fareSaved =  (Double) p.getAttributes().getAttribute("fareSaved");
			if(fareSaved>46.78345) {
				moneySaved++;
			}
		};
		System.out.println("Total number of sub optimal plan choice = " + subOptimalPlans);
		System.out.println("Total number of agents that saved money = " + moneySaved);
	}
	
	
	
	
	
}
