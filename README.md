# Ad Delivery API Validation Suite

REST Assured (Java) test suite validating **VAST 3.0 / VMAP** ad delivery contracts,
ad pod sequencing rules, frequency-capping enforcement, and p95 latency SLAs.

## What it tests

| Test Class | Coverage |
|---|---|
| `AdDeliveryTest` | VAST XML responses, impression pixel fire order, click-through HTTP 302 chains, MIME types, Duration format — across desktop / CTV / mobile / tablet |
| `AdPodSequencingTest` | Ad pod time-offset ordering, pre-roll before mid-roll, frequency cap (max 3/24h), p95 latency SLA (<200ms) |

## Stack
- **REST Assured 5.4** + **TestNG 7.9** (parallel execution via DataProvider)
- **Allure** for HTML reports
- **JMeter** (`jmeter/AdServerLoadTest.jmx`) for 500-concurrent load test

## Run

```bash
mvn clean test -Dsurefire.suiteXmlFiles=src/test/resources/testng.xml
```

## Load test (JMeter)
```bash
jmeter -n -t jmeter/AdServerLoadTest.jmx \
       -Jadserver.url=http://your-ad-server \
       -l results.jtl -e -o report/
```
