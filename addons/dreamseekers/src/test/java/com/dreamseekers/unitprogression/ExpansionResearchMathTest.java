package com.dreamseekers.unitprogression;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExpansionResearchMathTest {
 @Test void bonusesHaveNoUnresearchedOrGroundLeak(){
  assertEquals(12,ExpansionResearchMath.airDamage(10,true,true),.0001);
  assertEquals(10,ExpansionResearchMath.airDamage(10,true,false),.0001);
  assertEquals(10,ExpansionResearchMath.airDamage(10,false,true),.0001);
  assertEquals(125,ExpansionResearchMath.buildingBudget(100,true),.0001);
  assertEquals(100,ExpansionResearchMath.buildingBudget(100,false),.0001);
  assertEquals(3,ExpansionResearchMath.healing(4,true),.0001);
  assertEquals(4,ExpansionResearchMath.healing(4,false),.0001);
 }
}
