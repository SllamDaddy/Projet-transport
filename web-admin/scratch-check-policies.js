const { createClient } = require('@supabase/supabase-js');

const supabase = createClient(
  "https://pnqdwreqxdwcyggdioba.supabase.co",
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBucWR3cmVxeGR3Y3lnZ2Rpb2JhIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4NjUyOTE4MiwiZXhwIjoyMTAyMTA1MTgyfQ.Cr5cBB2cFlyaJvEMX5OuBTU-driQ6Sa5XQAtmC7t7G8"
);

async function run() {
  const { data, error } = await supabase.rpc('get_policies'); // or run raw query
  // Let's just execute a query using standard select if pg_policies is exposed, but usually it's not.
  // We can query pg_policies by selecting from a custom view or we can write a postgres script using supabase-cli.
  console.log("Error:", error);
}
run();
