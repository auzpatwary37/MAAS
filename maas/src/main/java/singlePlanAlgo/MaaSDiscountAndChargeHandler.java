package singlePlanAlgo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.handler.PersonDepartureEventHandler;
import org.matsim.api.core.v01.events.handler.PersonMoneyEventHandler;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.MatsimServices;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import com.google.inject.name.Named;

import MaaSPackages.MaaSPackage;
import MaaSPackages.MaaSPackages;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

//TODO Talk to enoch and incorporate personId here. 

public class MaaSDiscountAndChargeHandler implements PersonMoneyEventHandler, PersonDepartureEventHandler{

	
	private Scenario sceanrio;
	@Inject
	private @Named(MaaSUtil.MaaSPackagesAttributeName) MaaSPackages packages;
	private final EventsManager eventManager;
	
	@Inject
	MaaSDiscountAndChargeHandler(final MatsimServices controler, final TransitSchedule transitSchedule,
			Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc) {
		eventManager = controler.getEvents();
		this.sceanrio = controler.getScenario();
		
	}
	
	
	@Override
	public void handleEvent(PersonMoneyEvent event) {
		if(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_PURPOSE).equals(FareLink.FareTransactionName)) {//So, this is a fare payment event
			FareLink fl = new FareLink(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_TRANSACTION_PARTNER));
			Plan plan = this.sceanrio.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan();
			if(plan.getScore()==null) {
				if(plan.getAttributes().getAttribute("FareLinks")==null) {
					plan.getAttributes().putAttribute("FareLinks", new HashMap<String,Double>());
				}
				Map<String,Double> fareLinks = (Map<String, Double>) plan.getAttributes().getAttribute("FareLinks");
				fareLinks.compute(fl.toString(),(k,v)->(v==null)?1:v+1);//It will keep increasing. We should update it only once
			}
			
			double fare = Double.parseDouble(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_AMOUNT));
			double time = event.getTime(); // Obtain the time
			String chosenMaaSid = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			double fareRevenue = -1*fare;
			if(chosenMaaSid!=null) {
				double discount = Math.min(this.packages.getMassPackages().get(chosenMaaSid).getDiscountForFareLink(fl),-1*fare);
				fareRevenue -= discount;
				this.eventManager.processEvent(new PersonMoneyEvent(time,event.getPersonId(), discount,MaaSUtil.MaaSDiscountReimbursementTransactionName,fl.toString()));//Reimbursement Event
			}
			
			if(this.packages.getOperatorId(fl)!=null) {//the fare link might not be under any operators that are being optimized
				String fareLinkOperatorId = this.packages.getOperatorId(fl)+MaaSUtil.MaaSOperatorSubscript;
				this.eventManager.processEvent(new PersonMoneyEvent(time,Id.createPersonId(fareLinkOperatorId), fareRevenue, MaaSUtil.MaaSOperatorFareRevenueTransactionName,fl.toString()+"__"+event.getPersonId()));//Operator fare revenue event.
				Plan selectedOperatorPlan =  this.sceanrio.getPopulation().getPersons().get(Id.createPersonId(fareLinkOperatorId)).getSelectedPlan();
				Double oldReveneue = (Double)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName);
				if(oldReveneue == null) {
					selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorRevenueName, fareRevenue);
				}else {
					selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorRevenueName, oldReveneue+fareRevenue);
				}
			}
			
		}
	}

	@Override
	public void handleEvent(PersonDepartureEvent event) {
		if(!this.sceanrio.getPopulation().getPersons().containsKey(event.getPersonId())) {
			return; //Ignore the 'person' that is not actually a person (e.g. A bus driver) 
		}
		
		Plan plan = this.sceanrio.getPopulation().getPersons().get(event.getPersonId()).getSelectedPlan();
		String selectedMaaSid = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
		if(selectedMaaSid!=null) {
			MaaSPackage m = this.packages.getMassPackages().get(selectedMaaSid);
			double maasCost = m.getPackageCost();
			String packageOperatorId = m.getOperatorId()+MaaSUtil.MaaSOperatorSubscript;
			this.eventManager.processEvent(new PersonMoneyEvent(event.getTime(),event.getPersonId(), -maasCost, MaaSUtil.AgentpayForMaaSPackageTransactionName,m.getId()));//Agent buying package
			this.eventManager.processEvent(new PersonMoneyEvent(event.getTime(),Id.createPersonId(packageOperatorId), maasCost, MaaSUtil.MaaSOperatorpacakgeRevenueTransactionName,m.getId()+"__"+event.getPersonId()));//Operator earning revenue by selling package.
			
			Plan selectedOperatorPlan =  this.sceanrio.getPopulation().getPersons().get(Id.createPersonId(packageOperatorId)).getSelectedPlan();
			Double oldReveneue = (Double)selectedOperatorPlan.getAttributes().getAttribute(MaaSUtil.operatorRevenueName);
			if(oldReveneue == null) {
				selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorRevenueName, maasCost);
			}else {
				selectedOperatorPlan.getAttributes().putAttribute(MaaSUtil.operatorRevenueName, oldReveneue+maasCost);
			}
		}
	}
	
	
	
	
	
	
	
	
	

}
