# Suraksha - Women Safety Android App

A comprehensive Women Safety Android application built with Kotlin and Jetpack Compose for a 48-hour hackathon. This app provides essential safety features to help women feel secure and get help in emergency situations.

## 🛡️ Features

### Core Safety Features

1. **One-Tap SOS Button**
   - Large, prominent SOS button for quick emergency access
   - Automatically fetches current GPS location
   - Sends SMS with location to emergency contacts
   - Visual and haptic feedback when triggered

2. **Shake-to-Trigger SOS**
   - Uses accelerometer to detect 3 strong shakes
   - Automatically triggers SOS without touching the screen
   - Configurable sensitivity and shake count

3. **Fake Call Feature**
   - Simulates incoming calls with realistic ringtone
   - Customizable caller ID (default: "Mom")
   - Helps escape uncomfortable situations
   - Can be scheduled for 10, 30, or 60 seconds

4. **Safety Timer**
   - Set countdown timers (5, 10, 30 minutes)
   - Automatically triggers SOS if not cancelled
   - Visual countdown display
   - Perfect for walking home or meeting someone

5. **Emergency Recording**
   - Automatically records audio during SOS events
   - Stores recordings locally for privacy
   - Timestamped and linked to emergency events

### Optional Features

6. **Disguised Mode**
   - Makes the app look like a calculator
   - Long press "=" button triggers SOS secretly
   - Perfect for situations where you need to hide the app

## 🎨 UI/UX Features

- **Material Design 3 (Material You)** - Modern, adaptive UI
- **Dark Mode Support** - Automatic theme switching
- **Smooth Animations** - Button press effects and transitions
- **Intuitive Navigation** - Bottom navigation with 3 main sections
- **Responsive Design** - Works on all screen sizes

## 📱 Screens

### 1. Onboarding Screen
- Explains all app features
- Requests necessary permissions
- One-time setup for new users

### 2. Home Screen
- Large SOS button (center)
- Quick action buttons (Fake Call, Timer, Record)
- Location status card
- Emergency contacts overview
- Timer countdown display

### 3. Contacts Screen
- Add/edit up to 3 emergency contacts
- Contact validation
- Easy management interface

### 4. Settings Screen
- Toggle safety features
- App preferences
- Privacy settings
- Emergency information

## 🛠️ Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **Database**: Room Database (local storage)
- **Navigation**: Navigation Compose
- **Location**: Google Play Services Location
- **Permissions**: Runtime permission handling
- **Animations**: Compose Animation APIs

## 📋 Permissions Required

- **SMS**: Send emergency messages
- **Location**: Get GPS coordinates for emergency alerts
- **Microphone**: Record emergency audio
- **Storage**: Save recordings locally
- **Vibration**: Haptic feedback
- **Notifications**: Emergency alerts

## 🚀 Setup Instructions

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 24+ (API level 24)
- Google Play Services

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Suraksha
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the project folder

3. **Sync and Build**
   - Wait for Gradle sync to complete
   - Build the project (Build > Make Project)

4. **Run on Device/Emulator**
   - Connect an Android device or start an emulator
   - Click the Run button (green play icon)

### First Run

1. **Grant Permissions**
   - The app will request necessary permissions
   - Grant all permissions for full functionality

2. **Add Emergency Contacts**
   - Navigate to Contacts tab
   - Add up to 3 trusted contacts
   - These will receive SOS messages

3. **Test Features**
   - Try the SOS button (it won't send real SMS in debug mode)
   - Test shake detection
   - Try the fake call feature

## 🔧 Configuration

### Emergency Message Customization
Edit the SOS message in `SafetyService.kt`:
```kotlin
val message = """
    🚨 EMERGENCY SOS 🚨
    
    This is an emergency message from Suraksha Safety App.
    Time: $timestamp
    $locationText
    
    Please respond immediately!
""".trimIndent()
```

### Shake Sensitivity
Adjust shake detection in `ShakeDetector.kt`:
```kotlin
private const val SHAKE_THRESHOLD = 12.0f
private const val SHAKE_COUNT_THRESHOLD = 3
private const val SHAKE_TIME_WINDOW = 3000L
```

### Timer Options
Modify timer durations in `HomeScreen.kt`:
```kotlin
// Current: 5 minutes
onTimer(5)
```

## 🧪 Testing

### Manual Testing Checklist

- [ ] SOS button triggers correctly
- [ ] Location is fetched and displayed
- [ ] Emergency contacts are saved
- [ ] Fake call plays ringtone
- [ ] Timer counts down and triggers SOS
- [ ] Shake detection works
- [ ] App works in background
- [ ] Dark mode switches correctly
- [ ] All permissions are requested

### Debug Mode
- SMS sending is logged but not actually sent
- Location is simulated if GPS unavailable
- All features work without real emergency contacts

## 🔒 Privacy & Security

- **Local Storage**: All data stored on device only
- **No Cloud APIs**: Completely offline functionality
- **Permission Minimal**: Only requests necessary permissions
- **Data Encryption**: Room database can be encrypted
- **No Tracking**: No analytics or user tracking

## 🚨 Emergency Features

### SOS Trigger Methods
1. **Button Press**: Tap the large SOS button
2. **Shake Detection**: Shake phone 3 times
3. **Timer Expiry**: Automatic trigger after countdown
4. **Disguised Mode**: Long press calculator "=" button

### What Happens During SOS
1. **Location Fetch**: Gets current GPS coordinates
2. **SMS Send**: Sends emergency message to all contacts
3. **Recording Start**: Begins audio recording
4. **Visual Alert**: Button pulses and changes color
5. **Haptic Feedback**: Device vibrates

## 📞 Emergency Message Format

```
🚨 EMERGENCY SOS 🚨

This is an emergency message from Suraksha Safety App.
Time: [Current timestamp]
Location: [Google Maps link with coordinates]

Please respond immediately!
```

## 🎯 Future Enhancements

- **Voice Commands**: "Hey Google, trigger SOS"
- **Smart Location**: Geofencing for dangerous areas
- **Emergency Services**: Direct integration with 911
- **Wearable Support**: Smartwatch integration
- **Community Features**: Safe route sharing
- **AI Integration**: Smart threat detection

## 🤝 Contributing

This is a hackathon project, but contributions are welcome:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## ⚠️ Disclaimer

This app is designed for educational and hackathon purposes. In real emergency situations, always contact local emergency services (911 in the US) immediately. This app should be used as a supplementary safety tool, not a replacement for professional emergency services.

## 🆘 Emergency Contacts

For real emergencies:
- **US**: 911
- **UK**: 999
- **EU**: 112
- **India**: 100

---

**Built with ❤️ for Women's Safety**
