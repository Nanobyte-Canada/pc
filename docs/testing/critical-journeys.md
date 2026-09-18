# Critical Journeys

Date: 2026-09-17. Derived from `docs/superpowers/specs/2026-09-17-ui-testing-platform-design.md` section 6.5.

This list is the coverage denominator. Removal or demotion of any journey
requires owner sign-off recorded in this file.

| ID | Journey | Features and roles |
|---|---|---|
| CJ-01 | Authentication lifecycle: login, guard, logout, session expiry | authentication; anonymous, USER, ADMIN |
| CJ-02 | Dashboard overview: KPIs, positions, activities, account switching | dashboard; USER, ADMIN |
| CJ-03 | Portfolio management: model portfolios, custom builder, analysis | portfolio; USER, ADMIN |
| CJ-04 | Instrument discovery: screener filters, search, detail views | screener, instruments; USER, ADMIN |
| CJ-05 | Options trading: chain, strategy selection, leg builder, P&L chart, live quotes | options; USER, ADMIN |
| CJ-06 | Wheel strategy: calendar, KPIs, top tickers, order panel, chain panel | wheel; USER, ADMIN |
| CJ-07 | Broker connections: connect, sync, disconnect, positions, accounts | broker; USER, ADMIN |
| CJ-08 | Analytics: sector exposure, geography, top holdings, risk profile | analytics; USER, ADMIN |
| CJ-09 | Reporting: contributions, dividends, total value charts | reporting; USER, ADMIN |
| CJ-10 | Admin: data ingestion stats, workflows, run history | admin; ADMIN only |
| CJ-11 | Authorization boundaries: role x route matrix, positive and negative access | authorization; all roles |

## Change log

| Date | Change | Approved by |
|---|---|---|
| 2026-09-17 | Initial list | (pending approval) |
