package gg.modl.backend.beta;

import gg.modl.backend.limits.ServerLimits;
import gg.modl.backend.server.data.Server;

public record BetaTesterDetails(Server server, ServerLimits limits) {
}
