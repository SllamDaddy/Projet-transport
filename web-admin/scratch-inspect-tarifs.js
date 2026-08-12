const https = require('https');

const url = "https://pnqdwreqxdwcyggdioba.supabase.co/rest/v1/";
const options = {
  headers: {
    'apikey': 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBucWR3cmVxeGR3Y3lnZ2Rpb2JhIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NjUyOTE4MiwiZXhwIjoyMTAyMTA1MTgyfQ.Cr5cBB2cFlyaJvEMX5OuBTU-driQ6Sa5XQAtmC7t7G8'
  }
};

https.get(url, options, (res) => {
  let data = '';
  res.on('data', (chunk) => { data += chunk; });
  res.on('end', () => {
    try {
      const schema = JSON.parse(data);
      console.log("Tarifs definition:", schema.definitions.tarifs);
    } catch (e) {
      console.error(e);
    }
  });
}).on('error', (err) => {
  console.error(err);
});
