const { createClient } = require('@supabase/supabase-js');

const supabase = createClient(
  "https://pnqdwreqxdwcyggdioba.supabase.co",
  "sb_publishable_whHuPPByZUTGq3qsMsEpFw_UdV-fAZR"
);

async function run() {
  const { data, error } = await supabase.from('lignes').insert({
    nom: 'Test Anon Ligne',
    stations: [
      { id: 'station_1', name: 'la motte', latitude: 45.602024, longitude: 5.876759, approachRadius: 297, arrivalRadius: 80 }
    ],
    actif: true
  });
  console.log("Error:", error);
  console.log("Data:", data);
}
run();
