// Ortak Admin SDK başlatma.
// Gerçek proje: GOOGLE_APPLICATION_CREDENTIALS=./service-account.json (git'e girmez)
// Emulator: FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 (kimlik gerekmez)
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';
import { getAuth } from 'firebase-admin/auth';

const projectId = process.env.FIREBASE_PROJECT_ID || 'astronode-50a6c';
const useEmulator = !!process.env.FIRESTORE_EMULATOR_HOST;

initializeApp(useEmulator ? { projectId } : { credential: applicationDefault(), projectId });

export const db = getFirestore();
export const auth = getAuth();
export const APPLY = process.argv.includes('--apply');
export const log = (...a) => console.log(APPLY ? '[UYGULA]' : '[DENEME]', ...a);
