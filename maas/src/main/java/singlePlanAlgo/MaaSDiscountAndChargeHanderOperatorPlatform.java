package singlePlanAlgo;

import java.util.Map;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.controler.MatsimServices;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.transfer.TransferDiscountCalculator;
import optimizerAgent.MaaSUtil;
import transitCalculatorsWithFare.FareLink;

public class MaaSDiscountAndChargeHanderOperatorPlatform extends MaaSDiscountAndChargeHandler{

	MaaSDiscountAndChargeHanderOperatorPlatform(MatsimServices controler, TransitSchedule transitSchedule,
			Map<String, FareCalculator> fareCals, TransferDiscountCalculator tdc, Map<String, Double> govSubsidyRatio) {
		super(controler, transitSchedule, fareCals, tdc, govSubsidyRatio);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void handleEvent(PersonMoneyEvent event) {
		Id<Person> personId = event.getPersonId();
		if(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_PURPOSE).equals(FareLink.FareTransactionName)) {//So, this is a fare payment event
			FareLink fl = new FareLink(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_TRANSACTION_PARTNER));
			Person person = super.scenario.getPopulation().getPersons().get(personId);
			Plan plan = person.getSelectedPlan();
//			if(plan.getScore()==null) {
//				if(plan.getAttributes().getAttribute("FareLinks")==null) {
//					plan.getAttributes().putAttribute("FareLinks", new HashMap<String,Double>());
//				}
//				Map<String,Double> fareLinks = (Map<String, Double>) plan.getAttributes().getAttribute("FareLinks");
//				fareLinks.compute(fl.toString(),(k,v)->(v==null)?1:v+1);//It will keep increasing. We should update it only once
//			}
			
			double fare = Double.parseDouble(event.getAttributes().get(PersonMoneyEvent.ATTRIBUTE_AMOUNT));
			double time = event.getTime(); // Obtain the time
			String chosenMaaSid = (String) plan.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName);
			double fareRevenue = -1*fare;
			double discount = 0;
			if(chosenMaaSid!=null) {
				discount = Math.min(this.packages.getMassPackages().get(chosenMaaSid).getDiscountForFareLink(fl),-1*fare);
				fareRevenue -= discount;
				this.eventManager.processEvent(new PersonMoneyEvent(time,event.getPersonId(), discount,MaaSUtil.MaaSDiscountReimbursementTransactionName,fl.toString()));//Reimbursement Event
				double fareSaved = discount;
				if(person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.fareSavedAttrName)!=null) {
					fareSaved += (Double) person.getSelectedPlan().getAttributes().getAttribute(MaaSUtil.fareSavedAttrName);
				}				
				person.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, fareSaved);
			}else {
				person.getSelectedPlan().getAttributes().putAttribute(MaaSUtil.fareSavedAttrName, 0.); //If they didn't choose a plan, they saved nothing.
			}
			
			if(this.packages.getOperatorId(fl)!=null) {//the fare link might not be under any operators that are being optimized
				String fareLinkOperatorId = this.packages.getOperatorId(fl)+MaaSUtil.MaaSOperatorSubscript;
				if(this.packages.getMassPackages().get(chosenMaaSid).getSelfFareLinks().contains(fl)) {
					
				}else {
					
				}
				this.eventManager.processEvent(new PersonMoneyEvent(time,Id.createPersonId(fareLinkOperatorId), fareRevenue, MaaSUtil.MaaSOperatorFareRevenueTransactionName,fl.toString()+"__"+event.getPersonId()));//Operator fare revenue event.
				Plan selectedOperatorPlan =  this.scenario.getPopulation().getPersons().get(Id.createPersonId(fareLinkOperatorId)).getSelectedPlan();
				this.updateRevenue(fareRevenue, selectedOperatorPlan);
				this.updateOperatorTrip(selectedOperatorPlan);
				if(discount>0)this.updatePackageTrip(selectedOperatorPlan);
			}
		}
	}
}
