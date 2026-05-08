package com.dylanjohnpratt.paradise.be.health.model;

import java.math.BigDecimal;
import java.util.List;

public record Dataset(String label, List<BigDecimal> data) {}
