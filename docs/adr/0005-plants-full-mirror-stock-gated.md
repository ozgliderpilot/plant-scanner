# Plants tab is a full mirror, stock-gated

Access pushes in-stock accessions to the Sheet via `replacePlants`: clear and rewrite the whole `Plants` tab (stock > 0 only), never upsert. The device imports that list and replaces its local plant cache wholesale the same way — so offline lookups match what Access currently holds for sale/propagation.
