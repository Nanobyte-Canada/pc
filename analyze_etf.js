const data = JSON.parse(require('fs').readFileSync(process.env.TEMP + '/etf_tickers.json','utf8'));

// Check if well-known individual stocks appear
const stockTickers = ['AAPL','MSFT','GOOG','AMZN','TSLA','NVDA','META','JPM','V','WMT','BAC','DIS','NFLX','COST'];
const foundStocks = data.filter(d => stockTickers.includes(d.ticker));
console.log('Known stock tickers found:', foundStocks.length > 0 ? foundStocks.map(d => d.ticker + '(' + d.assetClass + ')') : 'NONE');

// Per asset class analysis
const classes = {};
data.forEach(d => {
  if (!classes[d.assetClass]) classes[d.assetClass] = [];
  classes[d.assetClass].push(d);
});

for (const [cls, items] of Object.entries(classes)) {
  console.log('\n=== ' + cls + ' (' + items.length + ') ===');
  console.log('Sample tickers:', items.slice(0, 15).map(d => d.ticker).join(', '));
  console.log('Sample names:', items.slice(0, 5).map(d => '  ' + d.ticker + ': ' + d.fund).join('\n'));

  // Check if names contain ETF/Fund keywords
  let hasEtf = 0, hasFund = 0, hasTrust = 0, hasShares = 0, hasNone = 0;
  const noKeyword = [];
  items.forEach(d => {
    const lower = d.fund.toLowerCase();
    if (lower.includes('etf')) hasEtf++;
    else if (lower.includes('fund')) hasFund++;
    else if (lower.includes('trust')) hasTrust++;
    else if (lower.includes('shares')) hasShares++;
    else { hasNone++; noKeyword.push(d); }
  });
  console.log('Name keywords: ETF=' + hasEtf + ' Fund=' + hasFund + ' Trust=' + hasTrust + ' Shares=' + hasShares + ' None=' + hasNone);
  if (noKeyword.length > 0) {
    console.log('No-keyword samples:', noKeyword.slice(0, 5).map(d => d.ticker + ': ' + d.fund).join(' | '));
  }
}

// Check for single-stock ETFs (they exist!)
const singleStock = data.filter(d => {
  const lower = d.fund.toLowerCase();
  return lower.includes('single') || lower.includes('tradr') || lower.includes('yieldmax') || lower.includes('defiance');
});
console.log('\n=== SINGLE-STOCK / DERIVATIVE ETFs (sample) ===');
console.log('Count matching keywords:', singleStock.length);
console.log('Samples:', singleStock.slice(0, 10).map(d => d.ticker + ': ' + d.fund + ' [' + d.assetClass + ']').join('\n'));
