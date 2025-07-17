PVA-i-fy
========


Explorations for a tool that allows reading Channel Access PVs via PV Access.

```
PVA client ----> PVA-i-fy -----> CA IOC
```

PVA-i-fy acts as a PVA server, detecting searches from PVA clients.
It turns these into CA searches, then provides the data received
from CA as PVA PVs.

- [x] Very first proof of principle:
      Create PV with initial value on first client access
- [ ] Update monitored value for 'double'
- [ ] Update monitored value for some more data types
- [ ] Throttling; Favor scalar updates over array updates
- [ ] Dispose PVs that no longer have a client (after delay...)
- [ ] Status PVs
