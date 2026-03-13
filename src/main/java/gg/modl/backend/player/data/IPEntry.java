package gg.modl.backend.player.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IPEntry {
    @Nullable
    private String ipAddress;

    @Nullable
    private String country;

    @Nullable
    private String region;

    @Nullable
    private String asn;

    private boolean proxy;

    private boolean hosting;

    @NotNull
    private Date firstLogin;

    @Builder.Default
    private List<Date> logins = new ArrayList<>();
}
