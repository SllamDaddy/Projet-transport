import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@supabase/supabase-js';

// Initialize the Supabase admin client using the service role key
const supabaseAdmin = createClient(
  process.env.NEXT_PUBLIC_SUPABASE_URL!,
  process.env.SUPABASE_SERVICE_ROLE_KEY!,
  {
    auth: {
      autoRefreshToken: false,
      persistSession: false
    }
  }
);

export async function POST(req: NextRequest) {
  try {
    // 1. Authentication Check: Verify that the caller is logged in and is an Admin
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      return NextResponse.json({ error: 'Non authentifié.' }, { status: 401 });
    }

    const token = authHeader.replace('Bearer ', '');
    const { data: { user }, error: userError } = await supabaseAdmin.auth.getUser(token);

    if (userError || !user) {
      return NextResponse.json({ error: 'Session invalide ou expirée.' }, { status: 401 });
    }

    // A driver must never be allowed to call this API to create other users
    if (user.user_metadata?.role === 'conducteur') {
      return NextResponse.json({ error: 'Accès refusé. Réservé aux administrateurs.' }, { status: 403 });
    }

    // 2. Parse request body
    const body = await req.json();
    const { nom, email, telephone, motDePasse } = body;

    if (!nom || !email || !motDePasse) {
      return NextResponse.json({ error: 'Le nom, l\'email et le mot de passe sont requis.' }, { status: 400 });
    }

    // 3. Create user account in Supabase Auth (Auto-Confirmed) with metadata role: 'conducteur'
    const { data: authUser, error: authError } = await supabaseAdmin.auth.admin.createUser({
      email,
      password: motDePasse,
      email_confirm: true,
      user_metadata: { role: 'conducteur' }
    });

    if (authError) {
      return NextResponse.json({ error: authError.message }, { status: 400 });
    }

    if (!authUser.user) {
      return NextResponse.json({ error: 'Une erreur inconnue est survenue lors de la création Auth.' }, { status: 500 });
    }

    // 4. Insert corresponding profile row into the 'conducteurs' database table
    const { error: dbError } = await supabaseAdmin
      .from('conducteurs')
      .insert({
        id: authUser.user.id, // References the Auth user's UUID
        nom,
        email,
        telephone: telephone || '',
        actif: true
      });

    if (dbError) {
      // Rollback auth user if DB insert fails
      await supabaseAdmin.auth.admin.deleteUser(authUser.user.id);
      return NextResponse.json({ error: `Erreur base de données: ${dbError.message}` }, { status: 400 });
    }

    return NextResponse.json({ success: true, user: authUser.user });
  } catch (error: any) {
    console.error('API Error in /api/conducteurs:', error);
    return NextResponse.json({ error: error.message || 'Une erreur interne est survenue.' }, { status: 500 });
  }
}
