PVA-i-fy
========


Explorations for a proxy that allows reading Channel Access (CA) PVs via PV Access (PVA).

The basic use case is a control system that's predominantly using CA,
and that information needs to be made available on a different network
via PVA. PVA is prepared for IPv6 as well as Transport Layer Security (TLS).
While the proxy is initially read-only, TLS will allow extending
the proxy for authenticated and authorized write access to the control system.

```
Office Network       #       Control System Network
'outside'            #       'inside'
                     #
PVA client ----> PVA-i-fy ----> CA IOC
                     #
                     #
```

PVA-i-fy runs on a host that has access to the two networks.
On the 'outside' network, PVA-i-fy acts as a PVA server,
detecting searches from PVA clients.
It turns these into CA searches on the 'inside' network,
then provides the data received from CA as PVA PVs.


 1. PVA client searches for PV
 2. PVA-i-fy, acting as a PVA server, receives that search.
    Note that PVA-i-fy does not reply to the search, yet,
    since it knows nothing about the PV
 3. PVA-i-fy creates CA client PV
 4. CA client PV connects to IOC, provides first sample as VType
 5. PVA-i-fy creates PVA server PV with that VType as initial value
 6. PVA client searches again since it has not received any reply, yet
 7. PVA-i-fy, acting as a PVA server, receives that search,
    and now has a matching PVA server PV, so it replies to the search,
    PVA client connects, gets initial value
 8. As the CA client PV receives value updates,
    PVA-i-fy forwards them by updating the associated PVA server PV

Note that PVA-i-fy will not reply to the initial search.
The initial search is used to locate the PV on the client side,
get the data type and initial value.
The PVA client is expected to keep searching.
Ideally, PVA-i-fy can reply to the second search,
assuming that the client side was able to locate
the PV and get data in time.

On its client side, PVA-i-fy uses the VType PV pool,
which fundamentally supports not only `ca://` but also
`sim://`, `mqtt://`, even `pva://`.
The main purpose of PVA-i-fy is the proxy from CA to
PVA, so `ca://` should be the default PV Pool prefix.
PVs named `XYZ` will be proxied from `ca://XYZ` to PVA `XYZ`,
and `sim://..` can be used for testing.

- [x] Very first proof of principle:
      Create PV with initial value on first client access
- [x] Update monitored value, time, alarm for 'double'
- [x] Dispose PVs that no longer have a client (on next update from client side)
- [ ] pom.xml
- [ ] Configure ports, use non-standard ports to avoid loops
- [ ] Update monitored value for some more data types, including display info etc.
- [ ] Throttling; Favor scalar updates over array updates
- [ ] Status PVs
- [ ] Controlled write access