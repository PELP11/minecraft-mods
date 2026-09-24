package com.afjan.arsenal.vehicle;

/** What the pilot's client asks of the jet for one tick: stick and rudder, throttle, brakes. */
public record PilotInput(FlightModel.Command command, double throttle, boolean brakes) {}
