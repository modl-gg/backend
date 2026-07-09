package gg.modl.backend.limits;

import gg.modl.backend.server.data.Server;

public interface ServerLimitPolicy {
    ServerLimits resolve(Server server);
}
