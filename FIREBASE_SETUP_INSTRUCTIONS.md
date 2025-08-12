# Firebase Realtime Database Setup Instructions

## Fix Permission Denied Errors

The "Permission denied" errors you're seeing are caused by Firebase Realtime Database security rules that are too restrictive.

### Step 1: Update Database Rules

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click on "Realtime Database" in the left sidebar
4. Click on the "Rules" tab
5. Replace the existing rules with the following:

```json
{
  "rules": {
    ".read": false,
    ".write": false,

    "users": {
      "$uid": {
        ".read": "auth != null && auth.uid == $uid",
        ".write": "auth != null && auth.uid == $uid"
      }
    },

    "bookings": {
      "$bookingId": {
        ".read": "auth != null && (data.child('userId').val() == auth.uid || newData.child('userId').val() == auth.uid)",
        ".write": "auth != null"
      }
    },

    "driver_locks": {
      "$driver": {
        "$slot": {
          ".read": "auth != null",
          ".write": "auth != null"
        }
      }
    },

    "drivers": {
      "$driverId": {
        ".read": "auth != null",
        ".write": "auth != null"
      }
    }
  }
}
```

6. Click "Publish" to save the rules

### Step 2: Test the App

After updating the rules:
1. Clean and rebuild your app
2. Try booking a ride again
3. The permission errors should be resolved

### What These Rules Do

- **users**: Users can only read/write their own data
- **bookings**: Authenticated users can create bookings, users can read their own bookings
- **driver_locks**: Authenticated users can read/write driver lock data
- **drivers**: Authenticated users can read/write driver information

### Alternative: More Permissive Rules (Development Only)

If you want to test quickly during development, you can use these more permissive rules (NOT recommended for production):

```json
{
  "rules": {
    ".read": "auth != null",
    ".write": "auth != null"
  }
}
```

⚠️ **Warning**: The permissive rules above allow any authenticated user to read/write all data. Only use for development/testing.

## Code Improvements Made

The app now includes:
- Better error handling with specific error messages
- Automatic retry for permission errors
- Fallback booking creation if transactions fail
- Comprehensive debugging logs

## Next Steps

1. Update your Firebase rules using the instructions above
2. Test the booking flow
3. Check the debug logs in Logcat for any remaining issues
